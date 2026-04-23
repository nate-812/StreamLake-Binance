package com.streamlake.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.streamlake.model.KlineBarView;
import com.streamlake.model.RiskTriggerView;
import com.streamlake.model.WhaleAlertView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 调用 ai-engine /diagnose 接口生成诊断报告。
 *
 * 上下文设计：将 K 线、巨鲸告警、风控触发三类数据一并发送给 AI，
 * 让模型基于真实数据输出有据可查的分析，而不是仅凭 symbol 名称空泛作答。
 *
 * ai-engine 请求体结构：
 * {
 *   "symbol": "BTCUSDT",
 *   "klines":       [ 最近 20 根 1min K 线（时序升序） ],
 *   "whaleAlerts":  [ 最近 10 条巨鲸告警 ],
 *   "riskTriggers": [ 最近 10 条风控触发 ]
 * }
 */
@Service
public class LlmDiagnosisService {

    private final RestClient restClient;
    private final String aiEngineBaseUrl;
    private final KlineService klineService;
    private final WhaleService whaleService;
    private final RiskService riskService;

    public LlmDiagnosisService(RestClient restClient,
                               @Value("${ai-engine.base-url:http://127.0.0.1:8000}") String aiEngineBaseUrl,
                               KlineService klineService,
                               WhaleService whaleService,
                               RiskService riskService) {
        this.restClient = restClient;
        this.aiEngineBaseUrl = aiEngineBaseUrl;
        this.klineService = klineService;
        this.whaleService = whaleService;
        this.riskService = riskService;
    }

    public String diagnose(String symbol) {
        // 聚合三类上下文数据，查询失败时返回空列表，不阻断诊断流程
        List<KlineBarView> klines = safeGet(() -> {
            List<KlineBarView> raw = klineService.listKlines(symbol, 20);
            // listKlines 返回降序，翻转为时序升序方便 AI 理解趋势
            java.util.Collections.reverse(raw);
            return raw;
        });
        List<WhaleAlertView> whales = safeGet(() -> whaleService.listAlerts(symbol, 10));
        List<RiskTriggerView> risks = safeGet(() -> riskService.listTriggers(symbol, 10));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("symbol", symbol);
        body.put("klines", klines);
        body.put("whaleAlerts", whales);
        body.put("riskTriggers", risks);

        try {
            JsonNode response = restClient.post()
                    .uri(aiEngineBaseUrl + "/diagnose")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            if (response != null && response.hasNonNull("reportMarkdown")) {
                return response.get("reportMarkdown").asText();
            }
            if (response != null && response.hasNonNull("report")) {
                return response.get("report").asText();
            }
        } catch (Exception ignored) {
            // ai-engine 未就绪时降级，不影响其他接口
        }

        return buildFallbackReport(symbol, klines, whales, risks);
    }

    /** 当 ai-engine 不可用时，用本地数据生成可读的降级报告 */
    private String buildFallbackReport(String symbol,
                                       List<KlineBarView> klines,
                                       List<WhaleAlertView> whales,
                                       List<RiskTriggerView> risks) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(symbol).append(" 诊断报告（降级模式）\n\n");
        sb.append("> AI Engine 暂未连通，以下为本地数据摘要，供参考。\n\n");

        if (!klines.isEmpty()) {
            KlineBarView first = klines.get(0);
            KlineBarView last = klines.get(klines.size() - 1);
            sb.append("## K 线摘要（最近 ").append(klines.size()).append(" 根 1min）\n");
            sb.append("- 区间开盘：").append(first.open()).append("\n");
            sb.append("- 最新收盘：").append(last.close()).append("\n");
            sb.append("- 区间最高：")
              .append(klines.stream().map(KlineBarView::high)
                      .max(java.math.BigDecimal::compareTo).orElse(last.high())).append("\n");
            sb.append("- 区间最低：")
              .append(klines.stream().map(KlineBarView::low)
                      .min(java.math.BigDecimal::compareTo).orElse(last.low())).append("\n\n");
        }

        if (!whales.isEmpty()) {
            sb.append("## 巨鲸告警（最近 ").append(whales.size()).append(" 条）\n");
            for (WhaleAlertView w : whales) {
                sb.append("- [").append(w.alertTime()).append("] ")
                  .append(w.direction()).append(" ")
                  .append(w.totalQuote()).append(" USDT")
                  .append("，严重度 ").append(w.severity()).append("\n");
            }
            sb.append("\n");
        }

        if (!risks.isEmpty()) {
            sb.append("## 风控触发（最近 ").append(risks.size()).append(" 条）\n");
            for (RiskTriggerView r : risks) {
                sb.append("- [").append(r.triggerTime()).append("] ")
                  .append("价格 ").append(r.price())
                  .append("，数量 ").append(r.qty()).append("\n");
            }
            sb.append("\n");
        }

        sb.append("---\n启动 ai-engine 后，接口将自动切换为模型生成报告。");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private <T> T safeGet(java.util.concurrent.Callable<T> callable) {
        try {
            return callable.call();
        } catch (Exception e) {
            return (T) java.util.List.of();
        }
    }
}
