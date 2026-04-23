package com.streamlake.scheduler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamlake.controller.RealtimeWSHandler;
import com.streamlake.model.KlineBarView;
import com.streamlake.model.WhaleAlertView;
import com.streamlake.service.KlineService;
import com.streamlake.service.WhaleService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class RealtimePushScheduler {

    private final WhaleService whaleService;
    private final KlineService klineService;
    private final JdbcTemplate jdbcTemplate;
    private final RealtimeWSHandler realtimeWSHandler;
    private final ObjectMapper objectMapper;

    private volatile LocalDateTime lastWhaleAlertTime = LocalDateTime.now().minusMinutes(5);

    public RealtimePushScheduler(WhaleService whaleService,
                                 KlineService klineService,
                                 JdbcTemplate jdbcTemplate,
                                 RealtimeWSHandler realtimeWSHandler,
                                 ObjectMapper objectMapper) {
        this.whaleService = whaleService;
        this.klineService = klineService;
        this.jdbcTemplate = jdbcTemplate;
        this.realtimeWSHandler = realtimeWSHandler;
        this.objectMapper = objectMapper;
    }

    /** 每 5s 推送新增巨鲸告警（增量，仅推上次推送时刻之后的数据） */
    @Scheduled(fixedDelayString = "${realtime.push.interval-ms:5000}")
    public void pushWhaleAlerts() {
        List<WhaleAlertView> recentAlerts = whaleService.listRecentAlerts(lastWhaleAlertTime, 50);
        if (recentAlerts.isEmpty()) return;

        LocalDateTime maxTime = recentAlerts.stream()
                .map(WhaleAlertView::alertTime)
                .max(LocalDateTime::compareTo)
                .orElse(lastWhaleAlertTime);
        lastWhaleAlertTime = maxTime;

        broadcast("whale.alert.batch", Map.of("count", recentAlerts.size(), "items", recentAlerts));
    }

    /**
     * 每 10s 推送活跃交易对的最新 K 线（全量快照，前端直接刷新最新一根蜡烛图）。
     * 活跃交易对定义：近 2 分钟内 kline_1min 有记录的 symbol，最多取 20 个。
     */
    @Scheduled(fixedDelay = 10_000)
    public void pushLatestKlines() {
        List<String> activeSymbols;
        try {
            activeSymbols = jdbcTemplate.queryForList("""
                    SELECT DISTINCT symbol FROM kline_1min
                    WHERE open_time >= NOW() - INTERVAL 2 MINUTE
                    LIMIT 20
                    """, String.class);
        } catch (Exception e) {
            return;
        }
        if (activeSymbols.isEmpty()) return;

        List<Map<String, Object>> snapshots = activeSymbols.stream()
                .map(sym -> {
                    try {
                        List<KlineBarView> bars = klineService.listKlines(sym, 1);
                        if (bars.isEmpty()) return null;
                        KlineBarView bar = bars.get(0);
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("symbol", bar.symbol());
                        m.put("openTime", bar.openTime());
                        m.put("open", bar.open());
                        m.put("high", bar.high());
                        m.put("low", bar.low());
                        m.put("close", bar.close());
                        m.put("volume", bar.volume());
                        m.put("quoteVolume", bar.quoteVolume());
                        m.put("tradeCount", bar.tradeCount());
                        return m;
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(m -> m != null)
                .toList();

        if (!snapshots.isEmpty()) {
            broadcast("kline.latest.batch", Map.of("items", snapshots));
        }
    }

    private void broadcast(String type, Map<String, Object> payload) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", type);
        message.putAll(payload);
        try {
            realtimeWSHandler.broadcast(objectMapper.writeValueAsString(message));
        } catch (JsonProcessingException ignored) {
        }
    }
}
