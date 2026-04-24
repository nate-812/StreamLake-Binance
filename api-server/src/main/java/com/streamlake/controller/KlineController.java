package com.streamlake.controller;

import com.streamlake.model.KlineBarView;
import com.streamlake.service.KlineService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/kline")
public class KlineController {

    private final KlineService klineService;

    public KlineController(KlineService klineService) {
        this.klineService = klineService;
    }

    @GetMapping("/{symbol}")
    public List<KlineBarView> listKline(@PathVariable String symbol,
                                        @RequestParam(defaultValue = "1min") String interval,
                                        @RequestParam(defaultValue = "100") int limit) {
        if (!"1min".equalsIgnoreCase(interval)) {
            throw new IllegalArgumentException("目前仅支持 interval=1min");
        }
        int safeLimit = Math.max(1, Math.min(limit, 1000));
        return klineService.listKlines(symbol, safeLimit);
    }
}
