package com.streamlake.model;

import jakarta.validation.constraints.NotBlank;

public record DiagnosisRequest(
        @NotBlank(message = "symbol 不能为空")
        String symbol
) {
}
