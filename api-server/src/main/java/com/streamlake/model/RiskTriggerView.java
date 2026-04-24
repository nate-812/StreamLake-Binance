package com.streamlake.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RiskTriggerView(
        String triggerId,
        String symbol,
        int ruleId,
        LocalDateTime triggerTime,
        BigDecimal price,
        BigDecimal qty
) {
}
