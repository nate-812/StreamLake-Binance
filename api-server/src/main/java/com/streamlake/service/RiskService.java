package com.streamlake.service;

import com.streamlake.model.RiskTriggerView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class RiskService {

    private final JdbcTemplate jdbcTemplate;

    public RiskService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<RiskTriggerView> listTriggers(String symbol, int limit) {
        if (symbol == null || symbol.isBlank()) {
            return jdbcTemplate.query("""
                    SELECT trigger_id, symbol, rule_id, trigger_time, price, qty
                    FROM risk_trigger
                    ORDER BY trigger_time DESC, trigger_id DESC
                    LIMIT ?
                    """, (rs, rowNum) -> map(rs), limit);
        }
        return jdbcTemplate.query("""
                SELECT trigger_id, symbol, rule_id, trigger_time, price, qty
                FROM risk_trigger
                WHERE symbol = ?
                ORDER BY trigger_time DESC, trigger_id DESC
                LIMIT ?
                """, (rs, rowNum) -> map(rs), symbol.toUpperCase(Locale.ROOT), limit);
    }

    private RiskTriggerView map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new RiskTriggerView(
                rs.getString("trigger_id"),
                rs.getString("symbol"),
                rs.getInt("rule_id"),
                rs.getTimestamp("trigger_time").toLocalDateTime(),
                rs.getBigDecimal("price"),
                rs.getBigDecimal("qty")
        );
    }
}
