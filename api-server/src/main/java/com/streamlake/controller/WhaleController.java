package com.streamlake.controller;

import com.streamlake.model.WhaleAlertView;
import com.streamlake.service.WhaleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/whale")
public class WhaleController {

    private final WhaleService whaleService;

    public WhaleController(WhaleService whaleService) {
        this.whaleService = whaleService;
    }

    @GetMapping("/alerts")
    public List<WhaleAlertView> listAlerts(
            @RequestParam(required = false) String symbol,
            @RequestParam(defaultValue = "100") int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 1000));
        return whaleService.listAlerts(symbol, safeLimit);
    }
}
