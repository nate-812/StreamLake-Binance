package com.streamlake.controller;

import com.streamlake.model.DiagnosisRequest;
import com.streamlake.model.DiagnosisRecordView;
import com.streamlake.model.DiagnosisResponse;
import com.streamlake.service.DiagnosisHistoryService;
import com.streamlake.service.LlmDiagnosisService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/ai")
public class DiagnosisController {

    private final LlmDiagnosisService diagnosisService;
    private final DiagnosisHistoryService diagnosisHistoryService;

    public DiagnosisController(LlmDiagnosisService diagnosisService,
                               DiagnosisHistoryService diagnosisHistoryService) {
        this.diagnosisService = diagnosisService;
        this.diagnosisHistoryService = diagnosisHistoryService;
    }

    @PostMapping("/diagnose")
    public DiagnosisResponse diagnose(@Valid @RequestBody DiagnosisRequest request) {
        String symbol = request.symbol().toUpperCase(Locale.ROOT);
        String report = diagnosisService.diagnose(symbol);
        String diagnosisId = diagnosisHistoryService.save(symbol, report);
        return new DiagnosisResponse(diagnosisId, symbol, report);
    }

    @GetMapping("/diagnosis/history")
    public List<DiagnosisRecordView> listHistory(@RequestParam String symbol,
                                                 @RequestParam(defaultValue = "20") int limit) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol 不能为空");
        }
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return diagnosisHistoryService.listHistory(symbol, safeLimit);
    }
}
