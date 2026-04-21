package com.streamlake.common.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Timestamp;

public class WhaleAlert implements Serializable {

    private String alertId;
    private String symbol;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private Timestamp alertTime;
    /** BUY or SELL */
    private String direction;
    private BigDecimal totalQuote;
    private int triggerCount;
    /** 1=警告 2=严重 3=极端 */
    private int severity;

    public String getAlertId() { return alertId; }
    public void setAlertId(String alertId) { this.alertId = alertId; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public Timestamp getAlertTime() { return alertTime; }
    public void setAlertTime(Timestamp alertTime) { this.alertTime = alertTime; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public BigDecimal getTotalQuote() { return totalQuote; }
    public void setTotalQuote(BigDecimal totalQuote) { this.totalQuote = totalQuote; }

    public int getTriggerCount() { return triggerCount; }
    public void setTriggerCount(int triggerCount) { this.triggerCount = triggerCount; }

    public int getSeverity() { return severity; }
    public void setSeverity(int severity) { this.severity = severity; }
}
