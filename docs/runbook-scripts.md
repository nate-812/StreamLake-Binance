# 运维脚本管理文档 (Runbook Scripts)

本目录 `ops/bin/` 提供了一套标准化、可审计的运维脚本，设计目标为：
- 避免管理员随意执行高风险 Shell 导致数据丢失或服务中断。
- 便于接入自动化运维工具（如 DataSentry）。
- 所有状态改变必须真实执行并校验结果；缺少二进制、JAR、配置或凭据时必须失败，禁止记录假成功。

## 1. 设计规范

所有的运维脚本都遵循以下原则：

1. **统一的环境变量**：所有的云端运行都会通过 `common.sh` 自动从 `/root/.streamlake-secrets` 加载密码和密钥。如果是本地测试环境，则会从工程目录的 `.env` 中加载。
2. **防重入、查事实**：如 `job.sh start <job>` 在提交任务前会首先查询 Flink 是否已有同名任务正在运行，若有，则拒绝再次提交。
3. **高危操作二次确认**：诸如重启、停止中间件（`doris.sh stop`、`kafka.sh restart` 等）操作默认均会报错并要求加上 `--confirm` 才能真正执行，防止误操作。
4. **日志留痕**：所有运维操作和结果，都会在 `/var/log/streamlake-ops/actions.log` 留存审计日志（本地测试会回退到 `/tmp`）。
5. **支持 Dry Run**：所有涉及写入或状态改变的命令可以通过加前缀 `DRY_RUN=1 ./script.sh ...` 来预演，验证脚本不会发生破坏。
6. **明确退出码**：所有脚本加上了 `set -euo pipefail`，遇到异常会立即抛出并返回非 0 状态码。
7. **路径可配置**：默认云端路径为 `/opt/StreamLake-Binance`、`/opt/flink`、`/opt/kafka`。如现场路径不同，必须通过 `STREAMLAKE_ROOT`、`FLINK_HOME`、`KAFKA_HOME`、`DORIS_FE_HOME`、`DORIS_BE_HOME` 等环境变量覆盖。

## 2. 脚本使用说明

### a. `common.sh`
通用功能库，仅被其他脚本 `source` 引用。不提供独立调用入口。

### b. `job.sh`
Flink 任务提交控制：
- 查看当前运行列表：`./job.sh status`
- 提交作业：`./job.sh start kline`。脚本会检查 Flink CLI、目标 JAR、必需凭据和同名 RUNNING 任务，任一条件不满足都会失败。
- 停止作业：`./job.sh stop kline --confirm`。脚本会解析 Flink Job ID 并执行 `flink cancel`。
- 支持的任务名：`kline`、`risk`、`whale`。

### c. `spring.sh` 与 `ai.sh`
后端 API 控制脚本：
- 查看状态：`./spring.sh status`
- 启动：`./spring.sh start`
- 停止：`./spring.sh stop --confirm`
- 它们均带有进程存活检查和接口健康度验证。启动后如果健康检查不通过，脚本会失败并指向日志文件。

### d. 基础组件控制 (`flink.sh`, `kafka.sh`, `doris.sh`)
- 仅提供高权限系统重启的封装，所有更改状态的操作必须带有 `--confirm`，例如 `./doris.sh restart --confirm`。
- `flink.sh` 调用 `$FLINK_HOME/bin/start-cluster.sh` 与 `stop-cluster.sh`。
- `kafka.sh` 调用 `$KAFKA_HOME/bin/kafka-server-start.sh -daemon $KAFKA_CONFIG` 与 `kafka-server-stop.sh`。
- `doris.sh` 默认使用 `$DORIS_FE_HOME/bin/start_fe.sh`、`$DORIS_BE_HOME/bin/start_be.sh` 等脚本；脚本会操作当前节点存在的 FE/BE 组件。如果现场 Doris 目录不同，必须显式配置环境变量。
