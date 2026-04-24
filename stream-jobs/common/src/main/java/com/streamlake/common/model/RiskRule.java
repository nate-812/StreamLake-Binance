package com.streamlake.common.model;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 风控规则，从 MySQL risk_rules 表加载。
 * symbol="*" 表示兜底规则，匹配所有交易对。
 */
public class RiskRule implements Serializable {

    private int id;
    /** 交易对，如 BTCUSDT；"*" 表示兜底规则 */
    private String symbol;
    /** 单笔成交数量上限，超过则触发风控 */
    private BigDecimal maxSingleQty;
    private boolean enabled;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public BigDecimal getMaxSingleQty() { return maxSingleQty; }
    public void setMaxSingleQty(BigDecimal maxSingleQty) { this.maxSingleQty = maxSingleQty; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
