package com.streamlake.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record WhaleAlertView(
        String alertId,
        String symbol,
        LocalDateTime alertTime,
        String direction,
        BigDecimal totalQuote,
        Integer triggerCount,
        Integer severity
) {
}
