package com.streamlake.model;

public record DiagnosisResponse(
        String diagnosisId,
        String symbol,
        String reportMarkdown
) {
}
