package com.streamlake.model;

import java.math.BigDecimal;

public record MarketSummaryView(
        String symbol,
        /** 最新收盘价 */
        BigDecimal lastPrice,
        /** 24h 价格变化（绝对值）*/
        BigDecimal priceChange24h,
        /** 24h 价格涨跌幅（百分比，如 2.35 表示 +2.35%）*/
        BigDecimal priceChangePct24h,
        /** 24h 成交量（基础币，如 BTC）*/
        BigDecimal volume24h,
        /** 24h 成交额（USDT）*/
        BigDecimal quoteVolume24h,
        /** 近 1h 巨鲸告警次数 */
        int whaleAlertCount1h,
        /** 近 24h 风控触发次数 */
        int riskTriggerCount24h
) {
}
