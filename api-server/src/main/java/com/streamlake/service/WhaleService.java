package com.streamlake.service;

import com.streamlake.model.WhaleAlertView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class WhaleService {

    private final JdbcTemplate jdbcTemplate;

    public WhaleService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<WhaleAlertView> listAlerts(String symbol, int limit) {
        if (symbol == null || symbol.isBlank()) {
            String sql = """
                    SELECT alert_id, symbol, alert_time, direction, total_quote, trigger_count, severity
                    FROM whale_alert
                    ORDER BY alert_time DESC, alert_id DESC
                    LIMIT ?
                    """;
            return jdbcTemplate.query(sql, (rs, rowNum) -> mapAlert(rs), limit);
        }

        String sql = """
                SELECT alert_id, symbol, alert_time, direction, total_quote, trigger_count, severity
                FROM whale_alert
                WHERE symbol = ?
                ORDER BY alert_time DESC, alert_id DESC
                LIMIT ?
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> mapAlert(rs),
                symbol.toUpperCase(Locale.ROOT), limit);
    }

    public List<WhaleAlertView> listRecentAlerts(LocalDateTime since, int limit) {
        String sql = """
                SELECT alert_id, symbol, alert_time, direction, total_quote, trigger_count, severity
                FROM whale_alert
                WHERE alert_time > ?
                ORDER BY alert_time DESC, alert_id DESC
                LIMIT ?
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> mapAlert(rs), since, limit);
    }

    private WhaleAlertView mapAlert(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new WhaleAlertView(
                rs.getString("alert_id"),
                rs.getString("symbol"),
                rs.getTimestamp("alert_time").toLocalDateTime(),
                rs.getString("direction"),
                rs.getBigDecimal("total_quote"),
                rs.getInt("trigger_count"),
                rs.getInt("severity")
        );
    }
}
