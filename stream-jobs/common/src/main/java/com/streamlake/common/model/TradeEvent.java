package com.streamlake.common.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TradeEvent implements Serializable {
    private String stream;
    private String symbol;
    private long eventTime;
    private long time;
    private long tradeId;
    private BigDecimal price;
    private BigDecimal qty;
    private BigDecimal quoteQty;
    private boolean isBuyerMaker;

    public String getStream() {
        return stream;
    }

    public void setStream(String stream) {
        this.stream = stream;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public long getEventTime() {
        return eventTime;
    }

    @JsonProperty("eventTime")
    public void setEventTime(long eventTime) {
        this.eventTime = eventTime;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public long getTradeId() {
        return tradeId;
    }

    @JsonProperty("tradeId")
    public void setTradeId(long tradeId) {
        this.tradeId = tradeId;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getQty() {
        return qty;
    }

    public void setQty(BigDecimal qty) {
        this.qty = qty;
    }

    public BigDecimal getQuoteQty() {
        return quoteQty;
    }

    @JsonProperty("quoteQty")
    public void setQuoteQty(BigDecimal quoteQty) {
        this.quoteQty = quoteQty;
    }

    @JsonProperty("isBuyerMaker")
    public boolean isBuyerMaker() {
        return isBuyerMaker;
    }

    @JsonProperty("isBuyerMaker")
    public void setBuyerMaker(boolean buyerMaker) {
        isBuyerMaker = buyerMaker;
    }
}
