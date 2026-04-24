package com.streamlake.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record KlineBarView(
        String symbol,
        LocalDateTime openTime,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal volume,
        BigDecimal quoteVolume,
        Integer tradeCount,
        Integer isClosed
) {
}
