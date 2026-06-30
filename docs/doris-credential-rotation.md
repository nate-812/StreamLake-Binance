# Doris 凭据轮换与账号管理方案

## 1. 当前风险
目前集群在代码中硬编码了默认的 `root` 账户与空密码。这意味着不仅生产代码以最高权限直接访问 Doris，而且由于过去默认密码为空，极易受到内网或外网的提权攻击。

## 2. 新账号模型设计

为了践行最小权限原则 (Principle of Least Privilege)，我们设计了以下账号模型：

- **`root`**: 仅作为“打破玻璃 (break-glass)”的超级管理员保留，不得在任何业务进程、脚本或配置文件中使用。
- **`streamlake_writer`**: 为 Flink 写入作业、Spring API 和 AI Engine 分配的业务账户。拥有对 `streamlake` 数据库的 DML 和 DDL 权限。
- **`datasentry_readonly`**: 为 DataSentry 自动化巡检 Agent 分配的账号，仅拥有全局的 `SELECT` 权限。

## 3. 轮换与迁移步骤

**前置准备**：代码已经过改造，支持从环境变量加载 `DORIS_USER` 和 `DORIS_PASSWORD`，这意味着修改密码不需要改代码，只需改环境变量。

**Step 1: 在 Doris 中创建新账户并授权**
（请由管理员在安全的本地终端执行，**切勿在此文档填写真实密码**）

```sql
-- 1. 创建业务读写账号
CREATE USER 'streamlake_writer'@'%' IDENTIFIED BY '此处填入新生成的高强度随机密码';
GRANT ALL ON streamlake.* TO 'streamlake_writer'@'%';

-- 2. 创建只读账号
CREATE USER 'datasentry_readonly'@'%' IDENTIFIED BY '此处填入另一高强度随机密码';
GRANT SELECT_PRIV ON streamlake.* TO 'datasentry_readonly'@'%';
```

**Step 2: 更新云端秘密文件**
在云主机上，编辑 `/root/.streamlake-secrets`，将 `DORIS_USER` 改为 `streamlake_writer`，并将 `DORIS_PASSWORD` 设为对应的新密码。

**Step 3: 重启业务进程**
使用 `ops/bin` 中的控制脚本，逐一重启服务：
```bash
# 重启 Spring 和 AI 引擎
./ops/bin/spring.sh restart --confirm
./ops/bin/ai.sh restart --confirm

# 停止并重新提交 Flink 作业（注意配合 Savepoint 或让最新 Offset 生效）
./ops/bin/job.sh stop kline --confirm
./ops/bin/job.sh start kline
```

**Step 4: 回收 root 密码**
确认所有业务已恢复正常且不再使用 `root` 后，在 Doris 中修改 `root` 密码：
```sql
SET PASSWORD FOR 'root'@'%' = PASSWORD('此处填入运维团队专用的超长强密码');
```

## 4. 验证命令
业务重启后，可以使用以下命令验证是否能用新账号正常访问：
```bash
mysql -h 192.168.1.10 -P 9030 -u streamlake_writer -p -e "SHOW DATABASES;"
```

## 5. 回滚步骤
如果迁移过程中发现某项服务无法启动，因为凭据有误或权限不足：
1. 立即回滚 `/root/.streamlake-secrets` 中的凭据到旧版本（或暂时指回 `root`，**前提是 root 密码暂未修改**）。
2. 重启失败的服务。
3. 排查授权缺失（例如某些作业需要特殊的系统表权限）。
