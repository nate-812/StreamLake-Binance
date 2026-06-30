package com.streamlake.service;

import com.streamlake.model.KlineBarView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Service
public class KlineService {

    private final JdbcTemplate jdbcTemplate;

    public KlineService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<KlineBarView> listKlines(String symbol, int limit) {
        String sql = """
                SELECT symbol, open_time, open, high, low, close, volume, quote_volume, trade_count, is_closed
                FROM kline_1min
                WHERE symbol = ?
                ORDER BY open_time DESC
                LIMIT ?
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> mapRow(rs), symbol.toUpperCase(Locale.ROOT), limit);
    }

    /** 批量取多个交易对各自的最新一根 K 线，一次查询替代 N 次 */
    public List<KlineBarView> listLatestKlines(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) return List.of();
        String placeholders = String.join(",", Collections.nCopies(symbols.size(), "?"));
        String sql = """
                SELECT k.symbol, k.open_time, k.open, k.high, k.low, k.close,
                       k.volume, k.quote_volume, k.trade_count, k.is_closed
                FROM kline_1min k
                INNER JOIN (
                    SELECT symbol, MAX(open_time) AS max_time
                    FROM kline_1min
                    WHERE symbol IN (%s)
                      AND open_time >= NOW() - INTERVAL 2 MINUTE
                    GROUP BY symbol
                ) latest ON k.symbol = latest.symbol AND k.open_time = latest.max_time
                """.formatted(placeholders);
        Object[] params = symbols.stream().map(s -> s.toUpperCase(Locale.ROOT)).toArray();
        return jdbcTemplate.query(sql, (rs, rowNum) -> mapRow(rs), params);
    }

    private KlineBarView mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new KlineBarView(
                rs.getString("symbol"),
                rs.getTimestamp("open_time").toLocalDateTime(),
                rs.getBigDecimal("open"),
                rs.getBigDecimal("high"),
                rs.getBigDecimal("low"),
                rs.getBigDecimal("close"),
                rs.getBigDecimal("volume"),
                rs.getBigDecimal("quote_volume"),
                rs.getInt("trade_count"),
                rs.getInt("is_closed")
        );
    }
}
