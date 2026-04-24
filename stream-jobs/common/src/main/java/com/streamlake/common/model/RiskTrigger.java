package com.streamlake.common.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Timestamp;

public class RiskTrigger implements Serializable {

    private String triggerId;
    private String symbol;
    private int ruleId;
    private Timestamp triggerTime;
    private BigDecimal price;
    private BigDecimal qty;

    public String getTriggerId() { return triggerId; }
    public void setTriggerId(String triggerId) { this.triggerId = triggerId; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public int getRuleId() { return ruleId; }
    public void setRuleId(int ruleId) { this.ruleId = ruleId; }

    public Timestamp getTriggerTime() { return triggerTime; }
    public void setTriggerTime(Timestamp triggerTime) { this.triggerTime = triggerTime; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public BigDecimal getQty() { return qty; }
    public void setQty(BigDecimal qty) { this.qty = qty; }
}
