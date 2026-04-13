package com.streamlake.cep;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.streamlake.common.deserializer.TradeEventDeserializer;
import com.streamlake.common.model.TradeEvent;
import com.streamlake.common.model.WhaleAlert;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.cep.CEP;
import org.apache.flink.cep.PatternSelectFunction;
import org.apache.flink.cep.PatternStream;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.SimpleCondition;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.legacy.RichSinkFunction;
import org.apache.flink.streaming.api.functions.sink.legacy.SinkFunction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Job-2：巨鲸活动 CEP 检测
 *
 * 检测逻辑：对同一交易对，在 60 秒内出现 3 笔及以上、
 * 单笔 quoteQty（成交额，USDT）超过阈值的交易，视为巨鲸活动。
 *
 * 输出：
 *   - Doris  streamlake.whale_alert 表（历史分析）
 *   - Kafka  streamlake.whale.alert  Topic（实时推送前端 WebSocket）
 *
 * Flink 2.0 关键点：
 *   - Pattern.within() 使用 java.time.Duration，不再使用已移除的 Time 类
 *   - RichSinkFunction / SinkFunction 从 legacy 包引入
 *   - open() 方法签名改为 open(OpenContext)
 *   - PatternSelectFunction 保持不变（CEP API 在 2.0 中稳定）
 */
public class WhaleCepJob {

    // ── 默认巨鲸阈值（USDT），可被 MySQL 配置覆盖 ──────────────────────
    private static final BigDecimal DEFAULT_THRESHOLD = new BigDecimal("10000");          // 未配置阈值的币种的默认阈值10000，BigDecimal的逻辑是将值原封不动的存进来，必须用字符串赋值，Double值的话会有精度问题

    public static void main(String[] args) throws Exception {

        final String kafkaBootstrap  = env("KAFKA_BOOTSTRAP",  "192.168.1.10:9092");
        final String sourceTopic     = env("KAFKA_TOPIC",       "binance.trade.raw");
        final String alertTopic      = env("ALERT_TOPIC",       "streamlake.whale.alert");
        final String consumerGroup   = env("KAFKA_GROUP",       "flink-cep-group");
        final String dorisJdbcUrl    = env("DORIS_JDBC_URL",    "jdbc:mysql://192.168.1.10:9030/streamlake?useSSL=false");
        final String dorisUser       = env("DORIS_USER",        "root");
        final String dorisPassword   = env("DORIS_PASSWORD",    "123456");
        final String mysqlJdbcUrl    = env("MYSQL_JDBC_URL",    "jdbc:mysql://192.168.1.10:3306/risk_control?useSSL=false");
        final String mysqlUser       = env("MYSQL_USER",        "root");
        final String mysqlPassword   = env("MYSQL_PASSWORD",    "123456");

        // ── 从 MySQL 预加载阈值配置 ──────────────────────────────────────
        Map<String, BigDecimal> thresholds = loadThresholdsFromMysql(
                mysqlJdbcUrl, mysqlUser, mysqlPassword);

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(3);
        env.enableCheckpointing(30_000);

        // ── Source：Kafka binance.trade.raw ─────────────────────────────
        KafkaSource<String> kafkaSource = KafkaSource.<String>builder()
                .setBootstrapServers(kafkaBootstrap)
                .setTopics(sourceTopic)
                .setGroupId(consumerGroup)
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        TradeEventDeserializer deserializer = new TradeEventDeserializer();

        DataStream<TradeEvent> trades = env
                .fromSource(kafkaSource, WatermarkStrategy.noWatermarks(), "kafka-trade-source")
                .map(deserializer::deserialize)
                .returns(TradeEvent.class)
                .filter(t -> t.getSymbol() != null && t.getPrice() != null && t.getQty() != null);

        // ── CEP Pattern：60 秒内 3 笔以上大单 ────────────────────────────
        // 注意：Pattern.within() 在 Flink 2.0 中接受 java.time.Duration
        Pattern<TradeEvent, ?> whalePattern = Pattern.<TradeEvent>begin("bigTrade")
                .where(new SimpleCondition<TradeEvent>() {
                    @Override
                    public boolean filter(TradeEvent trade) {
                        if (trade.getPrice() == null || trade.getQty() == null) return false;
                        // 用 price * qty 估算 quoteQty（兼容 quoteQty 可能为 null 的情况）
                        BigDecimal quote = trade.getQuoteQty() != null
                                ? trade.getQuoteQty()
                                : trade.getPrice().multiply(trade.getQty());       // 有quoteQty就直接用，没有就自己算
                        BigDecimal threshold = thresholds.getOrDefault(
                                trade.getSymbol().toUpperCase(Locale.ROOT),
                                thresholds.getOrDefault("*", DEFAULT_THRESHOLD));    // 优先币种阈值，其次*通用，最后是默认值10000
                        return quote.compareTo(threshold) > 0;                // BigDecimal是对象不能直接>,必须先compareTo
                    }
                })
                .timesOrMore(3)   // 至少触发 3 次
                .greedy()         // 贪婪匹配，尽可能多的大单，意思是如果在60秒前就已经有3笔大单了，不直接汇报，继续等更多大单
                .within(Duration.ofSeconds(60));

        // ── 对每个 symbol 分组后应用 CEP ────────────────────────────────
        PatternStream<TradeEvent> patternStream = CEP.pattern(
                trades.keyBy(t -> t.getSymbol().toUpperCase(Locale.ROOT)),
                whalePattern);

        SingleOutputStreamOperator<WhaleAlert> alerts = patternStream.select(
                new WhalePatternSelectFunction(thresholds));

        // ── Sink-1：Doris JDBC（whale_alert 历史表）──────────────────────
        final String dorisInsertSql =
                "INSERT INTO whale_alert " +
                "(alert_id, symbol, alert_time, direction, total_quote, trigger_count, severity) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        alerts.addSink(new DorisWhaleAlertSink(dorisJdbcUrl, dorisUser, dorisPassword, dorisInsertSql))
              .name("doris-whale-alert-sink");

        // ── Sink-2：Kafka（实时推送到 Spring Boot WebSocket）────────────
        KafkaSink<String> kafkaSink = KafkaSink.<String>builder()
                .setBootstrapServers(kafkaBootstrap)
                .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                        .setTopic(alertTopic)
                        .setValueSerializationSchema(new SimpleStringSchema())
                        .build())
                .build();

        alerts.map(alert -> new ObjectMapper()
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .writeValueAsString(alert))
              .sinkTo(kafkaSink)
              .name("kafka-whale-alert-sink");

        env.execute("streamlake-whale-cep");
    }

    // ── 从 MySQL whale_thresholds 表加载阈值 ─────────────────────────────
    private static Map<String, BigDecimal> loadThresholdsFromMysql(
            String jdbcUrl, String user, String password) {
        Map<String, BigDecimal> map = new HashMap<>();
        // 兜底规则：所有交易对默认阈值 10,000 USDT
        map.put("*", DEFAULT_THRESHOLD);
        // BTC 阈值更高
        map.put("BTCUSDT", new BigDecimal("50000"));
        map.put("ETHUSDT", new BigDecimal("20000"));

        try (Connection conn = DriverManager.getConnection(jdbcUrl, user, password);
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT symbol, threshold_quote FROM whale_thresholds WHERE enabled = 1")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                map.put(rs.getString("symbol").toUpperCase(Locale.ROOT),
                        rs.getBigDecimal("threshold_quote"));
            }
        } catch (Exception e) {
            // MySQL 表不存在时使用内置默认值，不阻断作业启动
            System.err.println("[WhaleCepJob] 无法从 MySQL 加载阈值，使用内置默认值: " + e.getMessage());
        }
        return map;
    }

    // ── CEP 命中后生成 WhaleAlert ─────────────────────────────────────────
    static class WhalePatternSelectFunction implements PatternSelectFunction<TradeEvent, WhaleAlert> {

        private final Map<String, BigDecimal> thresholds;

            WhalePatternSelectFunction(Map<String, BigDecimal> thresholds) {
            this.thresholds = thresholds;
        }

        @Override
        public WhaleAlert select(Map<String, List<TradeEvent>> pattern) {
            List<TradeEvent> matched = pattern.get("bigTrade");
            if (matched == null || matched.isEmpty()) return null;

            String symbol = matched.get(0).getSymbol().toUpperCase(Locale.ROOT);        // 已经keyBy过了，随便拿一笔交易获取symbol就行
            long latestTime = matched.stream()
                    .mapToLong(TradeEvent::getEventTime).max().orElse(System.currentTimeMillis());

            // 计算总成交额和主方向
            BigDecimal totalQuote = BigDecimal.ZERO;
            long buyCount = 0;
            for (TradeEvent t : matched) {
                BigDecimal q = t.getQuoteQty() != null
                        ? t.getQuoteQty()
                        : t.getPrice().multiply(t.getQty());
                totalQuote = totalQuote.add(q);
                // isBuyerMaker=false 意味着买方是 taker（主动买入）
                if (!t.isBuyerMaker()) buyCount++;
            }
            String direction = buyCount >= matched.size() / 2 + 1 ? "BUY" : "SELL";     // 看看买卖哪个多，就取哪个
            totalQuote = totalQuote.setScale(2, RoundingMode.HALF_UP);                // 保留2位小数

            // 严重程度：对比当前交易对阈值
            BigDecimal threshold = thresholds.getOrDefault(symbol,
                    thresholds.getOrDefault("*", new BigDecimal("10000")));
            int severity = 1;
            if (totalQuote.compareTo(threshold.multiply(new BigDecimal("5"))) >= 0) {
                severity = 3;
            } else if (totalQuote.compareTo(threshold.multiply(new BigDecimal("2"))) >= 0) {
                severity = 2;
            }

            WhaleAlert alert = new WhaleAlert();
            alert.setAlertId(UUID.randomUUID().toString());
            alert.setSymbol(symbol);
            alert.setAlertTime(new Timestamp(latestTime));
            alert.setDirection(direction);
            alert.setTotalQuote(totalQuote);
            alert.setTriggerCount(matched.size());
            alert.setSeverity(severity);
            return alert;
        }
    }

    // ── Doris JDBC Sink（Flink 2.0：legacy 包）────────────────────────────
    static class DorisWhaleAlertSink extends RichSinkFunction<WhaleAlert> {

        private final String jdbcUrl, username, password, sql;
        private transient Connection conn;
        private transient PreparedStatement ps;

        DorisWhaleAlertSink(String jdbcUrl, String username, String password, String sql) {
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
        public void invoke(WhaleAlert alert, SinkFunction.Context context) throws Exception {
            if (alert == null) return;
            ps.setString(1, alert.getAlertId());
            ps.setString(2, alert.getSymbol());
            ps.setTimestamp(3, alert.getAlertTime());
            ps.setString(4, alert.getDirection());
            ps.setBigDecimal(5, alert.getTotalQuote());
            ps.setInt(6, alert.getTriggerCount());
            ps.setInt(7, alert.getSeverity());
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
