package com.streamlake.service;

import com.streamlake.model.MarketSummaryView;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

@Service
public class MarketService {

    private final JdbcTemplate jdbcTemplate;

    public MarketService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public MarketSummaryView getSummary(String symbol) {
        String sym = symbol.toUpperCase(Locale.ROOT);

        BigDecimal lastPrice = queryScalar("""
                SELECT close FROM kline_1min
                WHERE symbol = ?
                ORDER BY open_time DESC LIMIT 1
                """, BigDecimal.class, sym);

        // 24h 前第一根 K 线的开盘价（作为基准价）
        BigDecimal openPrice24h = queryScalar("""
                SELECT open FROM kline_1min
                WHERE symbol = ? AND open_time >= NOW() - INTERVAL 24 HOUR
                ORDER BY open_time ASC LIMIT 1
                """, BigDecimal.class, sym);

        BigDecimal volume24h = queryScalar("""
                SELECT COALESCE(SUM(volume), 0)
                FROM kline_1min
                WHERE symbol = ? AND open_time >= NOW() - INTERVAL 24 HOUR
                """, BigDecimal.class, sym);

        BigDecimal quoteVolume24h = queryScalar("""
                SELECT COALESCE(SUM(quote_volume), 0)
                FROM kline_1min
                WHERE symbol = ? AND open_time >= NOW() - INTERVAL 24 HOUR
                """, BigDecimal.class, sym);

        int whaleCount1h = queryScalar("""
                SELECT COUNT(*) FROM whale_alert
                WHERE symbol = ? AND alert_time >= NOW() - INTERVAL 1 HOUR
                """, Integer.class, sym);

        int riskCount24h = queryScalar("""
                SELECT COUNT(*) FROM risk_trigger
                WHERE symbol = ? AND trigger_time >= NOW() - INTERVAL 24 HOUR
                """, Integer.class, sym);

        BigDecimal change = BigDecimal.ZERO;
        BigDecimal changePct = BigDecimal.ZERO;
        if (lastPrice != null && openPrice24h != null && openPrice24h.compareTo(BigDecimal.ZERO) != 0) {
            change = lastPrice.subtract(openPrice24h).setScale(8, RoundingMode.HALF_UP);
            changePct = change.divide(openPrice24h, 6, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        return new MarketSummaryView(
                sym,
                lastPrice,
                change,
                changePct,
                volume24h != null ? volume24h.setScale(8, RoundingMode.HALF_UP) : BigDecimal.ZERO,
                quoteVolume24h != null ? quoteVolume24h.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO,
                whaleCount1h != 0 ? whaleCount1h : 0,
                riskCount24h != 0 ? riskCount24h : 0
        );
    }

    private <T> T queryScalar(String sql, Class<T> type, Object... args) {
        try {
            return jdbcTemplate.queryForObject(sql, type, args);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }
}
