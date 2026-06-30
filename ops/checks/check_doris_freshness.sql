-- 检查 kline_1min 表最近一条记录的落后时间
SELECT 
    symbol,
    MAX(open_time) as last_open_time,
    TIMESTAMPDIFF(MINUTE, MAX(open_time), NOW()) as lag_minutes
FROM streamlake.kline_1min
GROUP BY symbol
ORDER BY lag_minutes DESC
LIMIT 20;
