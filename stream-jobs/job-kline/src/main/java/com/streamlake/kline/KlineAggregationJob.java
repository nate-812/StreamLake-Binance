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
import org.apache.flink.streaming.api.windowing.triggers.Trigger;
import org.apache.flink.streaming.api.windowing.triggers.TriggerResult;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;

import java.time.Duration;
import java.util.Set;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.Locale;

public class KlineAggregationJob {

    // BTC / ETH 交易量远超其他币种，单独走一路以避免 keyBy(symbol) 数据倾斜
    private static final Set<String> HIGH_VOLUME_SYMBOLS = Set.of("BTCUSDT", "ETHUSDT");

    public static void main(String[] args) throws Exception {
        final String kafkaBootstrap = env("KAFKA_BOOTSTRAP", "192.168.1.10:9092");
        final String kafkaTopic = env("KAFKA_TOPIC", "binance.trade.raw");
        final String consumerGroup = env("KAFKA_GROUP", "flink-kline-group");

        final String dorisJdbcUrl = env("DORIS_JDBC_URL", "jdbc:mysql://192.168.1.10:9030/streamlake?useSSL=false");   
        final String dorisUser = env("DORIS_USER", "streamlake_writer");
        final String dorisPassword = System.getenv("DORIS_PASSWORD");
        if (dorisPassword == null || dorisPassword.isBlank()) {
            throw new IllegalArgumentException("Missing required environment variable: DORIS_PASSWORD");
        }
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
                .map(deserializer::deserialize)
                .returns(TradeEvent.class)
                // null = 格式错误消息（deserializer 已静默处理），再过滤字段缺失的数据
                .filter(t -> t != null && t.getSymbol() != null && t.getPrice() != null && t.getQty() != null)
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<TradeEvent>forBoundedOutOfOrderness(Duration.ofSeconds(5))
                                .withTimestampAssigner((SerializableTimestampAssigner<TradeEvent>) (event, ts) -> event.getEventTime())
                );
                // forBoundedOutOfOrderness(Duration.ofSeconds(5))：设置水位线策略为有界无序，允许 5 秒的迟到数据。
                // withTimestampAssigner(...)：告诉 Flink 谁才是真正的执行时间，使用 event.getEventTime() 这意味着 K 线的时间是根据交易发生时的时间（事件时间）来算的，而不是根据 Flink 收到数据的时间。填补上68行未生成的水位线。

        // 2. 拆流：BTC/ETH 高频单独一路，其余 48 个低频共享一路，消除 keyBy(symbol) 数据倾斜
        //    两路使用完全相同的窗口聚合逻辑，最后 union 合并写入同一个 Sink。
        DataStream<TradeEvent> highVolTrades = trades.filter(
                t -> HIGH_VOLUME_SYMBOLS.contains(t.getSymbol().toUpperCase(Locale.ROOT)));
        DataStream<TradeEvent> lowVolTrades = trades.filter(
                t -> !HIGH_VOLUME_SYMBOLS.contains(t.getSymbol().toUpperCase(Locale.ROOT)));

        SingleOutputStreamOperator<KlineBar> klineHigh = highVolTrades
                .keyBy((KeySelector<TradeEvent, String>) t -> t.getSymbol().toUpperCase(Locale.ROOT))
                .window(TumblingEventTimeWindows.of(Duration.ofMinutes(1)))
                .trigger(new PeriodicAndFinalTrigger())
                .allowedLateness(Duration.ofSeconds(5))
                .sideOutputLateData(lateDataTag)
                .aggregate(new OhlcvAggregateFunction(), new KlineWindowFunction());

        SingleOutputStreamOperator<KlineBar> klineLow = lowVolTrades
                .keyBy((KeySelector<TradeEvent, String>) t -> t.getSymbol().toUpperCase(Locale.ROOT))
                .window(TumblingEventTimeWindows.of(Duration.ofMinutes(1)))
                .trigger(new PeriodicAndFinalTrigger())
                .allowedLateness(Duration.ofSeconds(5))
                .sideOutputLateData(lateDataTag)
                .aggregate(new OhlcvAggregateFunction(), new KlineWindowFunction());

        // 3. 合并两路结果，写入 Doris
        klineHigh.union(klineLow)
                .addSink(new DorisJdbcUpsertSink(dorisJdbcUrl, dorisUser, dorisPassword, dorisInsertSql))
                .name("doris-kline-sink");

        // 迟到数据：两路侧路输出合并打印
        klineHigh.getSideOutput(lateDataTag)
                .union(klineLow.getSideOutput(lateDataTag))
                .print("late-trade");

        // 执行 Flink 任务
        env.execute("streamlake-kline-aggregation");
    }

    /**
     * 批量写入 Sink：攒够 BATCH_SIZE 条或超过 FLUSH_INTERVAL_MS 自动 flush。
     * 相比逐条 executeUpdate()，批量 executeBatch() 可将 Doris 写入压力降低 10-20 倍。
     */
    static class DorisJdbcUpsertSink extends RichSinkFunction<KlineBar> {
        private static final int  BATCH_SIZE       = 50;
        private static final long FLUSH_INTERVAL_MS = 3_000L;

        private final String jdbcUrl;
        private final String username;
        private final String password;
        private final String sql;

        private transient Connection        conn;
        private transient PreparedStatement ps;
        private transient int               batchCount;
        private transient long              lastFlushMs;

        DorisJdbcUpsertSink(String jdbcUrl, String username, String password, String sql) {
            this.jdbcUrl   = jdbcUrl;
            this.username  = username;
            this.password  = password;
            this.sql       = sql;
        }

        @Override
        public void open(OpenContext openContext) throws Exception {
            Class.forName("com.mysql.cj.jdbc.Driver");
            conn = DriverManager.getConnection(jdbcUrl, username, password);
            conn.setAutoCommit(false);          // 批量提交，关闭自动提交
            ps   = conn.prepareStatement(sql);
            batchCount  = 0;
            lastFlushMs = System.currentTimeMillis();
        }

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
            ps.setInt(10, bar.isClosed() ? 1 : 0);
            ps.addBatch();
            batchCount++;

            long now = System.currentTimeMillis();
            if (batchCount >= BATCH_SIZE || (now - lastFlushMs) >= FLUSH_INTERVAL_MS) {
                flush();
            }
        }

        private void flush() throws Exception {
            if (batchCount == 0) return;
            ps.executeBatch();
            conn.commit();
            ps.clearBatch();
            batchCount  = 0;
            lastFlushMs = System.currentTimeMillis();
        }

        @Override
        public void close() throws Exception {
            try { flush(); } catch (Exception ignored) {}
            if (ps   != null) ps.close();
            if (conn != null) conn.close();
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
            OhlcvAccumulator acc = elements.iterator().next();
            KlineBar bar = new KlineBar();
            bar.setSymbol(key);
            bar.setOpenTime(new Timestamp(context.window().getStart()));
            bar.setOpen(scale(acc.open));
            bar.setHigh(scale(acc.high));
            bar.setLow(scale(acc.low));
            bar.setClose(scale(acc.close));
            bar.setVolume(scale(acc.volume));
            bar.setQuoteVolume(scale(acc.quoteVolume));
            bar.setTradeCount(acc.tradeCount);
            // 水位线已越过窗口末尾 → 窗口已关闭，标记为 closed
            boolean isClosed = context.currentWatermark() >= context.window().maxTimestamp();
            bar.setClosed(isClosed);
            out.collect(bar);
        }

        private BigDecimal scale(BigDecimal val) {
            return val.setScale(8, RoundingMode.HALF_UP);
        }
    }

    /**
     * 自定义 Trigger：
     *   - 每 5 秒触发一次中间结果（FIRE，不清除累加器）→ is_closed=0
     *   - 水位线到达窗口末尾时触发最终结果（FIRE，保留 allowedLateness 语义）→ is_closed=1
     * Doris UNIQUE KEY MoW 保证同 (symbol, open_time) 后写覆盖前写。
     * 5s 而非 2s：降低 Doris 写入频率，避免同步 JDBC Sink 过载反压。
     */
    static class PeriodicAndFinalTrigger extends Trigger<Object, TimeWindow> {
        private static final long INTERVAL_MS = 5_000L;

        @Override
        public TriggerResult onElement(Object element, long timestamp, TimeWindow window, TriggerContext ctx)
                throws Exception {
            // 注册窗口关闭的事件时间 timer（同一时间点，Flink 自动去重）
            ctx.registerEventTimeTimer(window.maxTimestamp());
            // 对齐到窗口起点的整数倍 interval，保证同一批 trade 注册同一个时间点
            // Flink 对相同 (key, time) 的 timer 只保留一个，彻底避免 timer 风暴
            long windowStart = window.getStart();
            long now         = ctx.getCurrentProcessingTime();
            long aligned     = windowStart + ((now - windowStart) / INTERVAL_MS + 1) * INTERVAL_MS;
            ctx.registerProcessingTimeTimer(Math.min(aligned, window.getEnd()));
            return TriggerResult.CONTINUE;
        }

        @Override
        public TriggerResult onProcessingTime(long time, TimeWindow window, TriggerContext ctx) throws Exception {
            // 继续注册下一个 2s timer，形成周期性触发
            ctx.registerProcessingTimeTimer(time + INTERVAL_MS);
            return TriggerResult.FIRE;          // 发出中间结果，累加器保留
        }

        @Override
        public TriggerResult onEventTime(long time, TimeWindow window, TriggerContext ctx) throws Exception {
            if (time == window.maxTimestamp()) {
                return TriggerResult.FIRE;      // 发出最终结果，由 allowedLateness 控制清理时机
            }
            return TriggerResult.CONTINUE;
        }

        @Override
        public void clear(TimeWindow window, TriggerContext ctx) throws Exception {
            ctx.deleteEventTimeTimer(window.maxTimestamp());
        }
    }
}
