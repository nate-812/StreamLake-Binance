package com.streamlake.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
public class LlmDiagnosisService {

    private final RestClient restClient;
    private final String aiEngineBaseUrl;

    public LlmDiagnosisService(RestClient restClient,
                               @Value("${ai-engine.base-url:http://127.0.0.1:8000}") String aiEngineBaseUrl) {
        this.restClient = restClient;
        this.aiEngineBaseUrl = aiEngineBaseUrl;
    }

    public String diagnose(String symbol) {
        try {
            JsonNode response = restClient.post()
                    .uri(aiEngineBaseUrl + "/diagnose")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("symbol", symbol))
                    .retrieve()
                    .body(JsonNode.class);

            if (response != null && response.hasNonNull("reportMarkdown")) {
                return response.get("reportMarkdown").asText();
            }
            if (response != null && response.hasNonNull("report")) {
                return response.get("report").asText();
            }
        } catch (Exception ignored) {
            // Phase 5 早期联调允许 AI 引擎尚未就绪，提供可读兜底结果。
        }

        return """
                # AI 诊断报告（降级）

                当前 AI Engine 尚未连通，返回基础诊断信息：
                - 交易对：%s
                - 建议：结合最近 5 分钟告警数、方向占比、K 线波动率进行人工复核。
                - 下一步：启动 ai-engine 后，接口将自动返回模型生成报告。
                """.formatted(symbol);
    }
}
