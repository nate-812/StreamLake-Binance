package com.streamlake.controller;

import com.streamlake.model.RiskTriggerView;
import com.streamlake.service.RiskService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/risk")
public class RiskController {

    private final RiskService riskService;

    public RiskController(RiskService riskService) {
        this.riskService = riskService;
    }

    @GetMapping("/triggers")
    public List<RiskTriggerView> listTriggers(
            @RequestParam(required = false) String symbol,
            @RequestParam(defaultValue = "100") int limit) {
        return riskService.listTriggers(symbol, Math.max(1, Math.min(limit, 1000)));
    }
}
