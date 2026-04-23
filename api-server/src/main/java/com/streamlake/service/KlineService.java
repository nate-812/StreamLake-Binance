package com.streamlake.service;

import com.streamlake.model.KlineBarView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

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
        return jdbcTemplate.query(sql, (rs, rowNum) -> new KlineBarView(
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
        ), symbol.toUpperCase(Locale.ROOT), limit);
    }
}
