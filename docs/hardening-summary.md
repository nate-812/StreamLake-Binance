# StreamLake-Binance 安全与可运维改造总结

## 基本信息
- **分支名**: `hardening/secure-operable-runtime`
- **提交记录**:
  - `11d2f13` docs: 增加网络暴露收口方案
  - `7dec085` docs: 增加Doris凭据轮换方案
  - `d426bd4` docs: 完善开发部署与监控说明
  - `ffe4818` feat: 增加DataSentry只读检查入口
  - `0e46439` feat: 增加可审计运维脚本模板
  - `343410a` fix: 统一Doris凭据加载
  - `c9242e4` chore: 增加运行配置模板
  - `b22d269` docs: 梳理运行安全边界

## 完成情况
- [x] 移除并脱敏了生产代码路径中的 `root` 与空密码默认值；历史规划文档仍可能保留旧部署背景，不能作为当前运行配置使用。
- [x] 统一通过环境变量 (`DORIS_USER` 等) 和 `/root/.streamlake-secrets` 注入凭据。
- [x] 制定了 Doris `streamlake_writer` 账号替换 `root` 的改密/轮换方案。
- [x] 新增了 `ops/bin` 运维脚本（带 `--confirm`、日志留痕、防重入和真实执行失败保护）。
- [x] 新增了 `ops/checks` 供 DataSentry 使用的只读巡检接口，支持现场路径和 Redis ACL 配置。
- [x] 梳理了网络安全组收口文档与监控告警建议。

## 未完成项 / TODO
- 云控制台手动修改安全组拦截公网端口尚未执行。
- 真实的生产 `root` 密码仍待手工重置。
- 引入 Terraform / IaC 自动化管理网络安全组。

## 用户手动在云控制台做的事项
1. 在阿里云/AWS控制台中，关闭针对 `9030, 3306, 6379, 8081, 9092` 等端口的 `0.0.0.0/0` 公网放行规则。
2. 登录 Doris FE 按照 `docs/doris-credential-rotation.md` 中的步骤建立新账号，修改 `root` 密码。

## 运行事实确认
- **是否发现秘密**：**是**。在原本的 `.java`、`application.yml` 与 `backfill_klines.py` 代码中发现了默认的 `root` 与空密码配置，存在极高安全风险。这些硬编码已在本次重构中移除，不会再默认启用。
- **是否修改了运行行为**：**是**。程序现在强制要求环境变量中包含 `DORIS_PASSWORD`，若未配置将抛错拒绝启动，而不是默认带着 root 无密码去撞库。`backfill_klines.py` 也从静默运行改为了必须附带 `--confirm` 的 Dry-Run 模式。
- **是否涉及生产重启**：**是**。改密后需依次重启 Spring API, AI Engine 并重新提交 Flink Jobs (建议带 Savepoint)。
- **验证结果**：已完成本地静态检查、脚本语法检查、Python 编译检查和 Maven 打包验证。真实云端执行仍需在维护窗口按脚本文档逐项 smoke。

## DataSentry 接入
DataSentry 现在可以直接调用 `ops/checks/*.sh` 获取稳定的 JSON 或文本格式的集群事实状态（如 Flink 任务存活、Doris 延迟等）。`ops/bin` 可作为未来人工审批 Runbook 的候选执行入口，但在进入自动白名单前必须完成云端 smoke、回滚演练和权限审计。
