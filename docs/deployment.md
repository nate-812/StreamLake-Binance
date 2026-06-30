# 部署指南

## 1. 后端与 Flink 部署

我们已经在 `ops/bin` 下准备了可审计的部署控制脚本。

在部署到云端主机时：
1. 请管理员先在 `/root/.streamlake-secrets` 文件中正确配置所有环境变量（参考 `.env.example`），并赋予 `chmod 600`。
2. 确认现场路径与默认值一致，或提前导出 `STREAMLAKE_ROOT`、`FLINK_HOME`、`KAFKA_HOME`、`DORIS_FE_HOME`、`DORIS_BE_HOME` 等覆盖变量。
3. 无论是提交 Flink 任务，还是重启 Spring Boot API，**禁止**手动敲打 Java/Flink 命令。
4. 必须通过 `ops/bin` 中的脚本执行，例如：
   ```bash
   ./ops/bin/job.sh start kline
   ./ops/bin/spring.sh restart --confirm
   ```
5. 新脚本首次上云时必须先执行 `status` 与 `DRY_RUN=1`，确认路径、JAR、凭据和健康检查都符合现场事实。

## 2. 前端部署

前端项目位于 `frontend` 目录。

构建：
```bash
cd frontend
npm install
npm run build
```

构建产物位于 `frontend/dist`。

**部署方式：**
- **建议**使用 Nginx 托管静态文件，并将 `/api` 路径反向代理到 Spring Boot API。
- 静态文件不包含任何敏感密钥，因为对模型的调用由后端 AI Engine 承担。

**Nginx 示例配置**：
```nginx
server {
    listen 80;
    server_name streamlake.internal;

    location / {
        root /var/www/streamlake-frontend;
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8080/;
        proxy_set_header Host $host;
    }
}
```
