package com.streamlake.common.deserializer;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamlake.common.model.TradeEvent;

import java.io.Serializable;

public class TradeEventDeserializer implements Serializable {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public TradeEvent deserialize(String json) {
        try {
            return MAPPER.readValue(json, TradeEvent.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize TradeEvent: " + json, e);
        }
    }
}
