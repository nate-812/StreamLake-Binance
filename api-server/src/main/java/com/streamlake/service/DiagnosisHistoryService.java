package com.streamlake.service;

import com.streamlake.model.DiagnosisRecordView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class DiagnosisHistoryService {

    private final JdbcTemplate jdbcTemplate;

    public DiagnosisHistoryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String save(String symbol, String reportMarkdown) {
        String diagnosisId = UUID.randomUUID().toString();
        String sql = """
                INSERT INTO ai_diagnosis (diagnosis_id, symbol, create_time, report_md)
                VALUES (?, ?, ?, ?)
                """;
        jdbcTemplate.update(
                sql,
                diagnosisId,
                symbol.toUpperCase(Locale.ROOT),
                LocalDateTime.now(),
                reportMarkdown
        );
        return diagnosisId;
    }

    public List<DiagnosisRecordView> listHistory(String symbol, int limit) {
        String sql = """
                SELECT diagnosis_id, symbol, create_time, report_md
                FROM ai_diagnosis
                WHERE symbol = ?
                ORDER BY create_time DESC, diagnosis_id DESC
                LIMIT ?
                """;
        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new DiagnosisRecordView(
                        rs.getString("diagnosis_id"),
                        rs.getString("symbol"),
                        rs.getTimestamp("create_time").toLocalDateTime(),
                        rs.getString("report_md")
                ),
                symbol.toUpperCase(Locale.ROOT),
                limit
        );
    }
}
