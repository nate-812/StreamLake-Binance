package com.streamlake.risk;

import com.streamlake.common.deserializer.TradeEventDeserializer;
import com.streamlake.common.model.RiskRule;
import com.streamlake.common.model.RiskTrigger;
import com.streamlake.common.model.TradeEvent;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.state.BroadcastState;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.state.ReadOnlyBroadcastState;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.BroadcastStream;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.co.BroadcastProcessFunction;
import org.apache.flink.streaming.api.functions.sink.legacy.RichSinkFunction;
import org.apache.flink.streaming.api.functions.sink.legacy.SinkFunction;
import org.apache.flink.streaming.api.functions.source.legacy.RichSourceFunction;
import org.apache.flink.util.Collector;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.sql.*;
import java.util.Locale;
import java.util.UUID;

/**
 * Job-3：实时风控广播流
 *
 * 架构说明：
 *   控制流（MySQL 规则）──► BroadcastStream ──┐
 *                                             ├─► BroadcastProcessFunction ──► RiskTrigger
 *   数据流（Kafka Trades）── rebalance() ────┘
 *
 * 核心亮点：
 *   1. MySQL risk_rules 表每 30 秒轮询一次，规则变更毫秒级热更新到所有 TaskManager
 *   2. BroadcastState 保证规则在所有并行实例间共享
 *   3. 风控判断无状态（每条交易独立对照广播规则），用 rebalance() 替代 keyBy(symbol)，
 *      将 BTCUSDT 等高频币种均匀打散到所有子任务，彻底消除数据倾斜
 *   4. 触发风控后：
 *      - 写 Redis 黑名单（TTL=24h）
 *      - 写 Doris risk_trigger 表（用于历史审计）
 *
 * Flink 2.0 适配：
 *   - RichSourceFunction 从 legacy 包引入（仍可用，deprecated 非 removed）
 *   - RichSinkFunction 从 legacy 包引入
 *   - open() 签名改为 open(OpenContext)
 */
public class RiskControlJob {

    /** BroadcastState 描述符，key=symbol（"*" 为兜底规则）*/
    static final MapStateDescriptor<String, RiskRule> RULE_STATE_DESC =
            new MapStateDescriptor<>("risk-rules", Types.STRING, Types.POJO(RiskRule.class));      // 为所有TaskManager创建统一的状态描述符，用于广播规则

    public static void main(String[] args) throws Exception {

        final String kafkaBootstrap = env("KAFKA_BOOTSTRAP",  "192.168.1.10:9092");
        final String sourceTopic    = env("KAFKA_TOPIC",       "binance.trade.raw");
        final String consumerGroup  = env("KAFKA_GROUP",       "flink-risk-group");
        final String dorisJdbcUrl   = env("DORIS_JDBC_URL",    "jdbc:mysql://192.168.1.10:9030/streamlake?useSSL=false");
        final String dorisUser      = env("DORIS_USER",        "root");
        final String dorisPassword  = env("DORIS_PASSWORD",    "");
        final String mysqlJdbcUrl   = env("MYSQL_JDBC_URL",    "jdbc:mysql://192.168.1.10:3306/risk_control?useSSL=false");
        final String mysqlUser      = env("MYSQL_USER",        "root");
        final String mysqlPassword  = env("MYSQL_PASSWORD",    "123456");
        final String redisHost      = env("REDIS_HOST",        "192.168.1.10");
        final int    redisPort      = Integer.parseInt(env("REDIS_PORT", "6379"));            // 从环境变量读取配置

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(3);
        env.enableCheckpointing(30_000);                               // 开启检查点，每30秒保存一次状态

        // ── 控制流：MySQL 规则（每 30s 重新加载，单并行度避免重复轮询）──────
        DataStream<RiskRule> rulesStream = env
                .addSource(new MySqlRulesSource(mysqlJdbcUrl, mysqlUser, mysqlPassword))      // 调用构造函数，将mysql连接参数赋值给成员变量
                .setParallelism(1)
                .name("mysql-rules-source");

        // 谁去看了Mysql规则，谁就广播给所有人
        BroadcastStream<RiskRule> broadcastRules = rulesStream.broadcast(RULE_STATE_DESC);

        // ── 数据流：Kafka binance.trade.raw ───────────────────────────────
        KafkaSource<String> kafkaSource = KafkaSource.<String>builder()
                .setBootstrapServers(kafkaBootstrap)
                .setTopics(sourceTopic)
                .setGroupId(consumerGroup)
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new SimpleStringSchema())            // 先把字节码转换为字符串，防止遇到异常数据导致解析失败，但这个中间对象可能很吃性能
                .build();

        TradeEventDeserializer deserializer = new TradeEventDeserializer();

        DataStream<TradeEvent> trades = env
                .fromSource(kafkaSource, WatermarkStrategy.noWatermarks(), "kafka-trade-source")
                .map(deserializer::deserialize)
                .returns(TradeEvent.class)                                   // 声明数据流类型，防止泛型擦除导致Flink调用低效的序列化器
                .filter(t -> t.getSymbol() != null && t.getQty() != null);

        // ── 双流 Connect：rebalance() + Broadcast ─────────────────────────
        // 风控判断是无状态的（每条交易独立对照广播规则，无需聚合历史），
        // 不需要 keyBy。改用 rebalance() 轮询分发，将 BTCUSDT 等高频币种
        // 均匀打散到所有子任务，彻底消除数据倾斜。
        DataStream<RiskTrigger> triggers = trades
                .rebalance()
                .connect(broadcastRules)
                .process(new RiskControlFunction());

        // ── Sink-1：Redis 黑名单（TTL = 24h）─────────────────────────────
        triggers.addSink(new RedisBlacklistSink(redisHost, redisPort))
                .name("redis-blacklist-sink");                       // 黑名单sink，顺便触发构造函数，将redis连接参数赋值给成员变量用于创建jedispool，下方同理

        // ── Sink-2：Doris risk_trigger 审计表 ────────────────────────────
        final String dorisInsertSql =   
                "INSERT INTO risk_trigger " +
                "(trigger_id, symbol, rule_id, trigger_time, price, qty) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        triggers.addSink(new DorisRiskTriggerSink(dorisJdbcUrl, dorisUser, dorisPassword, dorisInsertSql))
                .name("doris-risk-trigger-sink");

        env.execute("streamlake-risk-control");
    }

    // ── MySQL 规则轮询 Source（30s 间隔，Flink 2.0 legacy 包）────────────
    static class MySqlRulesSource extends RichSourceFunction<RiskRule> {

        private final String jdbcUrl, user, password;
        private volatile boolean running = true;

        MySqlRulesSource(String jdbcUrl, String user, String password) {
            this.jdbcUrl = jdbcUrl;
            this.user = user;
            this.password = password;
        }

        @Override
        public void open(OpenContext openContext) throws Exception {
            Class.forName("com.mysql.cj.jdbc.Driver");
        }

        @Override
        public void run(SourceContext<RiskRule> ctx) throws Exception {
            while (running) {
                try (Connection conn = DriverManager.getConnection(jdbcUrl, user, password);
                     PreparedStatement ps = conn.prepareStatement(                  // 短连接，每次轮询都重新建立连接，附加的好处是万一连接断开，每次轮询都能重连。
                             "SELECT id, symbol, max_single_qty FROM risk_rules WHERE enabled = 1")) {
                    ResultSet rs = ps.executeQuery();                   // 让ps把mysql查询结果（就是上行的select语句）拿出来，给rs指针
                    // synchronized 保证 checkpoint 期间不发射半批次规则
                    synchronized (ctx.getCheckpointLock()) {            // 拿着 checkpoint 锁发射数据，与 checkpoint 互斥，保证不会check到残缺的规则
                        while (rs.next()) {                             // 如果在一行有数据，就执行
                            RiskRule rule = new RiskRule();             // 创建规则对象，填充规则
                            rule.setId(rs.getInt("id"));
                            rule.setSymbol(rs.getString("symbol").toUpperCase(Locale.ROOT));
                            rule.setMaxSingleQty(rs.getBigDecimal("max_single_qty"));
                            rule.setEnabled(true);
                            ctx.collect(rule);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("[MySqlRulesSource] 规则加载失败: " + e.getMessage());
                }
                // 等待 30 秒后再次轮询
                for (int i = 0; i < 30 && running; i++) {            // 30秒轮询策略，选择每秒检查一次running，这样30秒内如果调用cancel()可以快速关闭不用等sleep
                    Thread.sleep(1_000);
                }
            }
        }
        // 用于 Flink 程序调用，取消轮询
        @Override
        public void cancel() {
            running = false;
        }
    }

    // ── 核心处理函数：BroadcastState + 风控判断 ───────────────────────────
    // 改为非键控版本 BroadcastProcessFunction，配合上游 rebalance() 消除数据倾斜。
    // 风控逻辑本身无需 per-key 状态，只读广播规则表，无需 KeyedBroadcastProcessFunction。
    static class RiskControlFunction
            extends BroadcastProcessFunction<TradeEvent, RiskRule, RiskTrigger> {

        @Override
        public void processElement(TradeEvent trade, ReadOnlyContext ctx, Collector<RiskTrigger> out)
                throws Exception {
            ReadOnlyBroadcastState<String, RiskRule> state = ctx.getBroadcastState(RULE_STATE_DESC);

            // 优先精确匹配，回退到兜底规则 "*"
            RiskRule rule = state.get(trade.getSymbol().toUpperCase(Locale.ROOT));
            if (rule == null) rule = state.get("*");
            if (rule == null) return;                                            // 三层判断，扑空的（null）继续判断，实在没有规则就放行

            if (trade.getQty().compareTo(rule.getMaxSingleQty()) > 0) {
                RiskTrigger trigger = new RiskTrigger();
                trigger.setTriggerId(UUID.randomUUID().toString());
                trigger.setSymbol(trade.getSymbol().toUpperCase(Locale.ROOT));
                trigger.setRuleId(rule.getId());
                trigger.setTriggerTime(new Timestamp(trade.getEventTime()));
                trigger.setPrice(trade.getPrice());
                trigger.setQty(trade.getQty());
                out.collect(trigger);                                           // 触发风控的交易，生成唯一id，记录币种、规则id、触发时间、价格、数量
            }
        }

        @Override
        public void processBroadcastElement(RiskRule rule, Context ctx, Collector<RiskTrigger> out)
                throws Exception {
            BroadcastState<String, RiskRule> state = ctx.getBroadcastState(RULE_STATE_DESC);
            state.put(rule.getSymbol(), rule);
            System.out.printf("[RiskControl] 规则热更新: symbol=%s, maxQty=%s%n",
                    rule.getSymbol(), rule.getMaxSingleQty());
        }                                                                           // 更改规则时触发
    }

    // ── Redis 黑名单 Sink ─────────────────────────────────────────────────
    static class RedisBlacklistSink extends RichSinkFunction<RiskTrigger> {

        private final String host;
        private final int port;
        private transient JedisPool jedisPool;

        RedisBlacklistSink(String host, int port) {
            this.host = host;
            this.port = port;
        }

        @Override
        public void open(OpenContext openContext) throws Exception {
            JedisPoolConfig config = new JedisPoolConfig();
            config.setMaxTotal(10);
            jedisPool = new JedisPool(config, host, port);                     // 还是老一套，用成员变量建立jedispool
        }

        @Override
        public void invoke(RiskTrigger trigger, SinkFunction.Context context) throws Exception {
            try (Jedis jedis = jedisPool.getResource()) {                      // try (...)连接一个jedis实例，用完会自动释放
                String key = "risk:blacklist:" + trigger.getSymbol();
                jedis.setex(key, 86400L, "1");  // TTL = 24 小时，黑名单持续24小时
            } catch (Exception e) {
                // Redis 写入失败不中断主流程，仅打印日志
                System.err.println("[RedisBlacklistSink] 写入失败: " + e.getMessage());
            }
        }

        @Override
        public void close() throws Exception {
            if (jedisPool != null) jedisPool.close();
        }
    }

    // ── Doris risk_trigger 审计 Sink ──────────────────────────────────────
    static class DorisRiskTriggerSink extends RichSinkFunction<RiskTrigger> {

        private final String jdbcUrl, username, password, sql;
        private transient Connection conn;
        private transient PreparedStatement ps;

        DorisRiskTriggerSink(String jdbcUrl, String username, String password, String sql) {
            this.jdbcUrl = jdbcUrl;
            this.username = username;
            this.password = password;
            this.sql = sql;
        }

        @Override
        public void open(OpenContext openContext) throws Exception {
            Class.forName("com.mysql.cj.jdbc.Driver");
            conn = DriverManager.getConnection(jdbcUrl, username, password);
            conn.setAutoCommit(true);
            ps = conn.prepareStatement(sql);
        }

        @Override
        public void invoke(RiskTrigger trigger, SinkFunction.Context context) throws Exception {
            ps.setString(1, trigger.getTriggerId());
            ps.setString(2, trigger.getSymbol());
            ps.setInt(3, trigger.getRuleId());
            ps.setTimestamp(4, trigger.getTriggerTime());
            ps.setBigDecimal(5, trigger.getPrice());
            ps.setBigDecimal(6, trigger.getQty());
            ps.executeUpdate();
        }

        @Override
        public void close() throws Exception {
            if (ps != null) ps.close();
            if (conn != null) conn.close();
        }
    }

    private static String env(String key, String defaultVal) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? defaultVal : v.trim();
    }
}
