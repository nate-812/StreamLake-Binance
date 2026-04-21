package com.streamlake.scheduler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamlake.controller.RealtimeWSHandler;
import com.streamlake.model.WhaleAlertView;
import com.streamlake.service.WhaleService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class RealtimePushScheduler {

    private final WhaleService whaleService;
    private final RealtimeWSHandler realtimeWSHandler;
    private final ObjectMapper objectMapper;

    private volatile LocalDateTime lastWhaleAlertTime = LocalDateTime.now().minusMinutes(5);

    public RealtimePushScheduler(WhaleService whaleService,
                                 RealtimeWSHandler realtimeWSHandler,
                                 ObjectMapper objectMapper) {
        this.whaleService = whaleService;
        this.realtimeWSHandler = realtimeWSHandler;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${realtime.push.interval-ms:5000}")
    public void pushWhaleAlerts() {
        List<WhaleAlertView> recentAlerts = whaleService.listRecentAlerts(lastWhaleAlertTime, 50);
        if (recentAlerts.isEmpty()) {
            return;
        }

        LocalDateTime maxTime = recentAlerts.stream()
                .map(WhaleAlertView::alertTime)
                .max(LocalDateTime::compareTo)
                .orElse(lastWhaleAlertTime);
        lastWhaleAlertTime = maxTime;

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", "whale.alert.batch");
        message.put("count", recentAlerts.size());
        message.put("items", recentAlerts);

        try {
            realtimeWSHandler.broadcast(objectMapper.writeValueAsString(message));
        } catch (JsonProcessingException ignored) {
            // 保底：序列化失败不影响后续调度。
        }
    }
}
