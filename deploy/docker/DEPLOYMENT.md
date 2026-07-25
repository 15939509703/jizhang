# Docker 部署说明

这份文档记录 `jizhang-server` 迁移到 Docker 后的部署和日常更新步骤。

当前服务器这套部署建议是：

- 后端应用用 Docker 跑
- MySQL 继续用现有的 `mysql8` 容器
- Redis 继续用现有的 `agentos-infra-redis-1` 容器

## 1. 目录说明

- `deploy/docker/Dockerfile`：后端镜像构建文件
- `deploy/docker/docker-compose.app.yml`：后端容器编排，复用现有 MySQL/Redis
- `deploy/docker/docker-compose.yml`：新环境全量编排，包含 MySQL、Redis、后端
- `deploy/docker/.env.example`：生产环境变量样例

## 2. 首次部署

1. 确保服务器已安装 Docker 和 Docker Compose。
2. 拉取或上传最新代码到服务器。
3. 进入仓库根目录 `jizhang-server`。
4. 如果服务器上还在跑旧的 `jizhang-server.service`，先停止并禁用它，避免 8081 端口冲突。
5. 准备服务器私有配置文件：

```bash
mkdir -p /etc/jizhang
cp deploy/jizhang-server.env.example /etc/jizhang/jizhang-server.env
```

6. 编辑 `/etc/jizhang/jizhang-server.env`，填入真实值：

- `MYSQL_ROOT_PASSWORD`
- `DB_PASSWORD`
- `REDIS_PASSWORD`
- `WX_APP_ID`
- `WX_APP_SECRET`
- `JWT_SECRET_BASE64`

7. 启动后端容器：

首次发布功能扩展版本前先备份数据库。应用启动时 Liquibase 会执行 `004-feature-expansion.sql`，新增 `fin_recurring_rule`、`fin_recurring_execution`，并为 `fin_account` 增加 `archived_time`。

```bash
docker compose -f deploy/docker/docker-compose.app.yml up -d --build
```

8. 查看后端日志：

```bash
docker compose -f deploy/docker/docker-compose.app.yml logs -f app
```

9. 检查健康状态：

```bash
curl http://127.0.0.1:8081/actuator/health
```

10. 如果服务器前面还有 Nginx，保持反代到 `127.0.0.1:8081`，以你当前 Nginx 配置为准。

11. 验证周期规则、账单日历、预算和资产接口后，再上传并发布微信小程序版本。

## 3. 以后改代码后的手动部署

每次后端代码有变更，按这个顺序执行：

1. 在服务器上更新代码。
2. 如果改动只涉及 Java 代码或资源文件，执行：

```bash
docker compose -f deploy/docker/docker-compose.app.yml up -d --build
```

3. 如果改动涉及数据库结构或基础数据，先确认代码里的 Liquibase 变更已提交，再执行上面的命令。
4. 如果改动涉及 `/etc/jizhang/jizhang-server.env` 里的配置，先改那个文件，再执行：

```bash
docker compose -f deploy/docker/docker-compose.app.yml up -d --force-recreate
```

5. 查看启动日志：

```bash
docker compose -f deploy/docker/docker-compose.app.yml logs -f app
```

6. 确认健康检查正常：

```bash
curl http://127.0.0.1:8081/actuator/health
```

## 4. 数据迁移和备份

### 4.1 数据库备份

备份当前 MySQL 容器数据：

```bash
docker exec -i mysql8 sh -c 'MYSQL_PWD="$(cat /run/secrets/mysql_root_password)" mysqldump -uroot jizhang_db' > jizhang_db_$(date +%F).sql
```

### 4.2 附件备份

后端附件保存在容器卷 `uploads` 中。迁移前请单独备份这个卷，或者把卷内容拷出再迁移。

### 4.3 从旧部署切到 Docker

如果你是从宿主机 `java -jar + systemd` 切换过来：

1. 先停掉旧服务。
2. 备份旧数据库和附件目录。
3. 启动 Docker 版服务。
4. 如果 Redis 也换了容器，旧的 refresh token 会失效，用户需要重新登录。

### 4.4 功能扩展配置

```text
FEATURE_RECURRING_TRANSACTIONS=true
FEATURE_TRANSACTION_CALENDAR=true
FEATURE_BUDGET_FORECAST=true
FEATURE_ASSET_DASHBOARD=true
RECURRING_SCAN_INTERVAL_MS=300000
```

周期扫描默认每 5 分钟执行一次。临时设置 `FEATURE_RECURRING_TRANSACTIONS=false` 可停止领取新的周期任务，已经成功生成的账单不会被删除或冲正。

## 5. 回滚

如果新版本启动失败：

1. 回到上一版代码。
2. 重新执行：

```bash
docker compose -f deploy/docker/docker-compose.app.yml up -d --build
```

3. 如果还是不行，先看：

```bash
docker compose -f deploy/docker/docker-compose.app.yml logs --tail 200 app
```

4. 必要时先停掉容器再回滚：

```bash
docker compose -f deploy/docker/docker-compose.app.yml stop app
```

`004-feature-expansion.sql` 已执行后，回滚应用镜像时保留新增表和字段，不执行 `DROP TABLE` 或删除执行历史。需要紧急关闭时，先关闭周期任务配置并重建容器，再回滚前端入口或后端镜像。

## 6. 注意事项

- `SERVER_ADDRESS` 必须是 `0.0.0.0`，容器内不能继续用 `127.0.0.1`。
- `JWT_SECRET_BASE64` 要固定，不要每次部署都换。
- `SPRING_LIQUIBASE_ENABLED` 建议保持 `true`，便于自动迁移。
- 如果你换了 Redis 或 MySQL 的持久化卷，现有登录态会失效，需要重新登录。
