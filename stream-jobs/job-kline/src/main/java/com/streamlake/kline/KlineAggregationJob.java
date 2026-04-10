package com.streamlake.kline;

import com.streamlake.common.deserializer.TradeEventDeserializer;
import com.streamlake.common.model.KlineBar;
import com.streamlake.common.model.TradeEvent;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.legacy.RichSinkFunction;
import org.apache.flink.streaming.api.functions.sink.legacy.SinkFunction;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;

import java.time.Duration;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.Locale;

public class KlineAggregationJob {
    public static void main(String[] args) throws Exception {
        final String kafkaBootstrap = env("KAFKA_BOOTSTRAP", "192.168.1.10:9092");
        final String kafkaTopic = env("KAFKA_TOPIC", "binance.trade.raw");
        final String consumerGroup = env("KAFKA_GROUP", "flink-kline-group");

        final String dorisJdbcUrl = env("DORIS_JDBC_URL", "jdbc:mysql://192.168.1.10:9030/streamlake?useSSL=false");   
        final String dorisUser = env("DORIS_USER", "root");
        final String dorisPassword = env("DORIS_PASSWORD", "");             //定义重要的常量，调用辅助方法env获取环境变量值，如果系统无变量，则使用后边的默认值
        final String dorisInsertSql = "INSERT INTO kline_1min " +
                "(symbol, open_time, open, high, low, close, volume, quote_volume, trade_count, is_closed) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";                              // Doris 插入 SQL 语句 ，？占位符对应 KlineBar 字段，由于现在还没有清洗出KlineBar，所以先用？占位。由于有ps，在后续写入数据库时，有妙用。

        //环境创建，设置并行度为3
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(3);

        // Kafka Source 配置
        KafkaSource<String> source = KafkaSource.<String>builder()
                .setBootstrapServers(kafkaBootstrap)
                .setTopics(kafkaTopic)
                .setGroupId(consumerGroup)
                .setStartingOffsets(OffsetsInitializer.latest())                 // 从 Kafka 最新 数据开始读取
                .setValueOnlyDeserializer(new SimpleStringSchema())              // 定义 Kafka 值反序列化器
                .build();

        // 定义侧路输出标签，用于迟到数据
        OutputTag<TradeEvent> lateDataTag = new OutputTag<>("late-data") {};              // 定义侧路输出标签，用于迟到数据，{}是个匿名内部类，很重要，避免泛型擦除

        // 从 Kafka 读取数据，反序列化为 TradeEvent，过滤无效数据，提取事件时间并生成水印
        TradeEventDeserializer deserializer = new TradeEventDeserializer();          // 定义 TradeEvent 反序列化器deserializer ，用于从 Kafka 反序列化交易事件

        // 1. 从 Kafka 读取原始交易数据，转为 TradeEvent Java 对象。
        DataStream<TradeEvent> trades = env.fromSource(source, WatermarkStrategy.noWatermarks(), "trade-kafka-source")
                //env.fromSource：这是启动抽水机的开关。它告诉 Flink 按照之前定义的 source（Kafka 配置）去拉取数据。
                //WatermarkStrategy.noWatermarks()：当前数据为json，无法从黑盒中提取事件时间，所以先不生成水位线，等后续处理再生成。
                //trade-kafka-source：这是抽水机的名称，用于在 Flink UI 中识别它。
                .map(deserializer::deserialize)                           // 调用 TradeEvent 反序列化器deserializer 反序列化 Kafka 值为 TradeEvent 对象
                .returns(TradeEvent.class)                                  // 声明返回的类型为 TradeEvent，帮助 Flink 正确处理类型信息
                .filter(t -> t.getSymbol() != null && t.getPrice() != null && t.getQty() != null)   
                // 过滤无效数据，只保留 symbol、price、qty 都不为 null 的交易事件
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<TradeEvent>forBoundedOutOfOrderness(Duration.ofSeconds(5))
                                .withTimestampAssigner((SerializableTimestampAssigner<TradeEvent>) (event, ts) -> event.getEventTime())
                );
                // forBoundedOutOfOrderness(Duration.ofSeconds(5))：设置水位线策略为有界无序，允许 5 秒的迟到数据。
                // withTimestampAssigner(...)：告诉 Flink 谁才是真正的执行时间，使用 event.getEventTime() 这意味着 K 线的时间是根据交易发生时的时间（事件时间）来算的，而不是根据 Flink 收到数据的时间。填补上68行未生成的水位线。

        // 2. 按 symbol 分组，窗口聚合 1 分钟数据
        SingleOutputStreamOperator<KlineBar> kline1m = trades       
                .keyBy((KeySelector<TradeEvent, String>) t -> t.getSymbol().toUpperCase(Locale.ROOT))    // 按 symbol 分组，转换为大写，ROOT 是为了避免不同语言环境下大小写转换不一致的问题
                .window(TumblingEventTimeWindows.of(Duration.ofMinutes(1)))     // 1 分钟滚动窗口
                .allowedLateness(Duration.ofSeconds(5))     // 允许 5 秒的迟到数据，65s——70s，这部分数据来一条窗口重新计算一次
                .sideOutputLateData(lateDataTag)            // 将迟到数据输出到侧路输出，标签为 lateDataTag
                .aggregate(new OhlcvAggregateFunction(), new KlineWindowFunction());
                //OhlcvAggregateFunction（增量处理）：来一个交易事件，就更新一次 K 线，而不是等窗口结束再更新，这样可以减少内存占用和计算压力。
                //KlineWindowFunction（窗口处理）：等窗口结束了，才把增量处理函数算好的的 K 线数据（klineBar对象）输出到下游。

        // 3. 写入 Doris 数据库
        kline1m.addSink(new DorisJdbcUpsertSink(dorisJdbcUrl, dorisUser, dorisPassword, dorisInsertSql))
                .name("doris-kline-sink");          // 将 KlineBar 数据写入Doris，注意，这一步会调用外部的 DorisJdbcUpsertSink 构造函数，从而将

        // 迟到数据先打日志，后续 Phase 3 可接独立侧路 sink
        kline1m.getSideOutput(lateDataTag).print("late-trade");     //迟到数据先在控制台打印了，看日志判断系统是否正常

        // 执行 Flink 任务
        env.execute("streamlake-kline-aggregation");
    }

    // 自定义 SinkFunction，将 KlineBar 数据写入 Doris 数据库
    static class DorisJdbcUpsertSink extends RichSinkFunction<KlineBar> {        //使用Rich函数，因为它提供了 open() 和 close() 方法，可以管理 JDBC 连接的生命周期，只open 一次，避免每次写入都建立连接和关闭连接。
        private final String jdbcUrl;
        private final String username;
        private final String password;
        private final String sql;               // final修饰成员变量，保证在任务中稳定不变，当jobmanager读取任务配置时会调用构造函数，被 115 行的构造函数赋值。

        private transient Connection conn;
        private transient PreparedStatement ps;        // transient 修饰 JDBC 连接和 PreparedStatement，这两个是连接（指针），只在本地有效，所以打上 transient 标记，拒绝序列化。    那么怎么在 taskmanager 们使用呢？ 在执行 open() 方法时，方法发现 conn 和 ps 都是 null，于是用已经传过来的 jdbcUrl 等参数建立连接和创建 PreparedStatement。

        // 构造函数，初始化 JDBC 连接
        DorisJdbcUpsertSink(String jdbcUrl, String username, String password, String sql) {
            this.jdbcUrl = jdbcUrl;
            this.username = username;
            this.password = password;
            this.sql = sql;
        }                                       //这个构造函数在 main 方法中被调用，传入了 Doris 的 JDBC 连接参数和 SQL 语句(???的那个)，将 main 中的局部变量赋值给这个类的成员变量，才能让 taskmanager 们使用， 这个真是太精妙了。

        // 打开连接，创建 PreparedStatement
        @Override
        public void open(OpenContext openContext) throws Exception {
            Class.forName("com.mysql.cj.jdbc.Driver");                             // 激活一下 JDBC 驱动类
            conn = DriverManager.getConnection(jdbcUrl, username, password);       // 建立连接，使用的正是 main 方法中传入的参数。
            conn.setAutoCommit(true);                                              // 设置自动提交，每条都直接写入 Doris。
            ps = conn.prepareStatement(sql);                                      // 把预编译的 SQL 语句（???占位的那个）发给数据库，让数据库预先写好带？？占位符的执行计划，等待 invoke 方法填充参数。因为ps本身是基于内存的指针，且依附于conn，所以和conn一样不能被序列化。
        }

        // 执行写入操作，每条 KlineBar 数据对应一次 SQL 执行
        @Override
        public void invoke(KlineBar bar, SinkFunction.Context context) throws Exception {
            ps.setString(1, bar.getSymbol());
            ps.setTimestamp(2, bar.getOpenTime());
            ps.setBigDecimal(3, bar.getOpen());
            ps.setBigDecimal(4, bar.getHigh());
            ps.setBigDecimal(5, bar.getLow());
            ps.setBigDecimal(6, bar.getClose());
            ps.setBigDecimal(7, bar.getVolume());
            ps.setBigDecimal(8, bar.getQuoteVolume());
            ps.setInt(9, bar.getTradeCount());
            ps.setBoolean(10, true);             // 精准打入数据库的 sql 语句的占位符。
            ps.executeUpdate();
        }


        // 关闭 PreparedStatement 和 Connection
        @Override
        public void close() throws Exception {
            if (ps != null) {
                ps.close();             // 关闭 PreparedStatement，告诉数据库，我不玩了，刚刚给你预编译的 sql 你扔了吧。    
            }
            if (conn != null) {
                conn.close();          // 关闭 Connection，不连Doris了。    
            }
        }
    }

    // 辅助方法，从环境变量获取配置，提供默认值
    private static String env(String key, String defaultVal) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? defaultVal : v.trim();
    }

    // 自定义窗口聚合函数，用于计算 1 分钟 Kline 数据
    static class OhlcvAccumulator {                 // 是个累加器，1分钟窗口创建，结束销毁
        String symbol;
        BigDecimal open;
        BigDecimal high;
        BigDecimal low;
        BigDecimal close;                           // 这些需要来一个时间赋值，所以先给null
        BigDecimal volume = BigDecimal.ZERO;
        BigDecimal quoteVolume = BigDecimal.ZERO;
        int tradeCount = 0;                         // 初始值为0
    }

    static class OhlcvAggregateFunction implements AggregateFunction<TradeEvent, OhlcvAccumulator, OhlcvAccumulator> {
        @Override
        public OhlcvAccumulator createAccumulator() {
            return new OhlcvAccumulator();
        }                                                //这个就是窗口开始时创建累加器的方法，返回一个新的 OhlcvAccumulator 对象。

        @Override
        public OhlcvAccumulator add(TradeEvent t, OhlcvAccumulator acc) {
            BigDecimal p = t.getPrice();
            BigDecimal q = t.getQty();
            BigDecimal quote = t.getQuoteQty() == null ? p.multiply(q) : t.getQuoteQty();

            if (acc.tradeCount == 0) {
                acc.symbol = t.getSymbol().toUpperCase(Locale.ROOT);
                acc.open = p;
                acc.high = p;
                acc.low = p;
                acc.close = p;
            } else {
                acc.high = acc.high.max(p);
                acc.low = acc.low.min(p);
                acc.close = p;
            }                          // 累加器更新的逻辑，我一一遍就写出来了，嘻嘻，厉害吧。

            acc.volume = acc.volume.add(q);
            acc.quoteVolume = acc.quoteVolume.add(quote);            // 这两个add用的很精妙，拿来算一下就扔，万花丛中过，片叶不沾身，又能把数据稳稳更新。
            acc.tradeCount += 1;
            return acc;
        }

        @Override
        public OhlcvAccumulator getResult(OhlcvAccumulator acc) {
            return acc;
        }                                                       //getResult 结束本次计算，我们的acc已经是结果，无需处理，直接返回就行了。

        @Override
        public OhlcvAccumulator merge(OhlcvAccumulator a, OhlcvAccumulator b) {    // 当遇到合并两个累加器的情况时，调用这个方法
            if (a.tradeCount == 0) {
                return b;
            }
            if (b.tradeCount == 0) {
                return a;
            }
            OhlcvAccumulator out = new OhlcvAccumulator();
            out.symbol = a.symbol;
            out.open = a.open;
            out.high = a.high.max(b.high);
            out.low = a.low.min(b.low);
            out.close = b.close;
            out.volume = a.volume.add(b.volume);
            out.quoteVolume = a.quoteVolume.add(b.quoteVolume);
            out.tradeCount = a.tradeCount + b.tradeCount;
            return out;
        }
    }

    // 全量窗口函数
    static class KlineWindowFunction extends ProcessWindowFunction<OhlcvAccumulator, KlineBar, String, TimeWindow> {
        @Override
        public void process(String key, Context context, Iterable<OhlcvAccumulator> elements, Collector<KlineBar> out) {
            OhlcvAccumulator acc = elements.iterator().next();              //原本这是个可迭代对象，但增量处理已经算好数据 acc对象，所以.next() 取唯一对象。
            KlineBar bar = new KlineBar();
            bar.setSymbol(key);
            bar.setOpenTime(new Timestamp(context.window().getStart()));
            bar.setOpen(scale(acc.open));
            bar.setHigh(scale(acc.high));
            bar.setLow(scale(acc.low));
            bar.setClose(scale(acc.close));
            bar.setVolume(scale(acc.volume));
            bar.setQuoteVolume(scale(acc.quoteVolume));
            bar.setTradeCount(acc.tradeCount);                        // 加上时间戳，并给数据整容
            bar.setClosed(true);                                      // 设置为true，表示这个 K 线已经收盘，不会再变了。
            out.collect(bar);
        }

        private BigDecimal scale(BigDecimal val) {
            return val.setScale(8, RoundingMode.HALF_UP);
        }                                                           //增量处理为了保持精度有很多小数，这里统一设置小数位为8位，四舍五入。
    }
}
