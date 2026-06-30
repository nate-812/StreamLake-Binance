# hardening: 收口运行凭据并增强可运维性

## PR 目的
本项目中存在严重的安全风险：多个 Flink Job 和后端服务静默使用 `root` 与空密码去连接 Doris 和 MySQL。此外，生产部署缺乏可审计的运维管控方案。
本 PR 依照指令书进行了一次系统级的安全与可运维性重构。

## 改动内容
1. 废除了生产代码路径中的 `root` 及空密码默认值。引入了强制的环境变量 (`DORIS_PASSWORD` 等) 检查。
2. 新增了 `ops/bin` 标准化控制脚本，带有操作日志 (`/var/log/...`)、`--confirm` 二次确认、`DRY_RUN` 测试、禁止 Job 重复提交和真实执行失败保护。
3. 提供了 `ops/checks` 供自动化运维 (如 DataSentry) 安全提取线上事实指标 (只读方式)，并支持现场路径和 Redis ACL 配置。
4. 补充了 `.env.example`，明确了云端凭据的统一挂载方式。
5. 梳理并输出了包括「网络暴露收口」、「Doris 密码安全轮换」、「排障与回滚」在内的完整交付文档集。

## 部署注意事项
- **Breaking Change**: 更新部署后，直接运行现有服务会报错。必须在主机的 `/root/.streamlake-secrets` 中填写了有效的密码凭据，并通过脚本 `source` 后方可启动。
- 建议配合阅读 `docs/doris-credential-rotation.md` 以平滑切换 Doris 账号。
- 合并前仍需在云端维护窗口验证 `ops/bin` 的真实启停路径；未完成 smoke 前不要加入自动执行白名单。
