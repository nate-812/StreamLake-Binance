-- 检查当前风控规则
SELECT id, symbol, max_single_qty, enabled 
FROM risk_control.risk_rules;
