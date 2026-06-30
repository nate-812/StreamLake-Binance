package com.streamlake.common.deserializer;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamlake.common.model.TradeEvent;

import java.io.Serializable;

public class TradeEventDeserializer implements Serializable {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public TradeEvent deserialize(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return MAPPER.readValue(json, TradeEvent.class);
        } catch (Exception e) {
            // 跳过格式错误的消息，不抛异常不崩 Subtask，水位线正常推进
            return null;
        }
    }
}
