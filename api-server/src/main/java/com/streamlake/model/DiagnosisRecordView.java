package com.streamlake.model;

import java.time.LocalDateTime;

public record DiagnosisRecordView(
        String diagnosisId,
        String symbol,
        LocalDateTime createTime,
        String reportMarkdown
) {
}
