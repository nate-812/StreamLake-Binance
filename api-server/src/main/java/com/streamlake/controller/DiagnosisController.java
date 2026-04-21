package com.streamlake.controller;

import com.streamlake.model.DiagnosisRequest;
import com.streamlake.model.DiagnosisResponse;
import com.streamlake.service.LlmDiagnosisService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
@RequestMapping("/api/ai")
public class DiagnosisController {

    private final LlmDiagnosisService diagnosisService;

    public DiagnosisController(LlmDiagnosisService diagnosisService) {
        this.diagnosisService = diagnosisService;
    }

    @PostMapping("/diagnose")
    public DiagnosisResponse diagnose(@Valid @RequestBody DiagnosisRequest request) {
        String symbol = request.symbol().toUpperCase(Locale.ROOT);
        String report = diagnosisService.diagnose(symbol);
        return new DiagnosisResponse(symbol, report);
    }
}
