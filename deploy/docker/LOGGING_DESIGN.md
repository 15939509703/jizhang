# Docker 日志管理接入设计方案

日期：2026-10-28  
状态：设计方案，尚未实施  
适用范围：`jizhang-server` Docker 部署，后续可扩展到其他项目。

## 1. 目标与完成标准

在不需要本地重新运行后端的情况下，查询服务器上已产生的应用日志，定位指定接口、HTTP 状态、异常内容和请求 `traceId`。第一阶段的完成标准：

1. 能按时间、项目、环境、容器和接口路径筛选访问日志。
2. 能按 HTTP `2xx`、`4xx`、`5xx` 筛选，并结合请求方法、路径和耗时定位问题。
3. 能搜索应用已输出的任意日志文本、异常类名和 `traceId`；能查看匹配日志及相邻时间段的日志。
4. 重启日志服务后仍能查询留存期内的日志；短时停止日志服务再恢复后，验证缓冲范围内的补送情况。

不在第一阶段范围内：链路追踪、告警、业务审计、实时业务指标、按项目分权、记录请求或响应体。日志系统只能查询**已经输出并成功采集**的内容，不能还原未记录的变量或请求体。

## 2. 现状与方案选择

- `deploy/docker/Dockerfile` 直接启动 Java 进程；当前日志输出到容器标准输出，无需先改为文件日志。
- `deploy/docker/docker-compose.app.yml` 以 `network_mode: host` 运行 `jizhang-app`，生产环境设置 `HTTP_LOG_INCLUDE_BODY=false`。
- `TraceIdFilter` 为请求设置 `traceId`，通过 `X-Trace-Id` 响应头返回，并记录 `HTTP {method} {path} status={status} durationMs={duration}`；`application.yml` 将 `traceId` 写进日志级别格式。
- `ApiResponse` 有业务 `code`，成功值为 `"0"`；现有生产访问日志仅记录 HTTP 状态，不记录业务 `code`。

选择 **Vector + OpenObserve**：Vector 从 Docker 采集、解析并发送日志；OpenObserve 保存和查询日志。两者使用独立于应用的 Compose 部署，应用的发布、回滚不重建日志服务。初期与应用同机部署；其他服务器的项目接入时，每台应用服务器部署采集器，统一发送到日志服务。`jizhang-app` 的 host 网络模式不影响通过 Docker API 采集日志。

```text
jizhang-app / 其他项目 → Docker 日志 → 每台应用主机的 Vector
                                          ↓
                                OpenObserve 持久化存储与查询
```

项目早期架构设计稿提及 Loki，属于当时的候选规划；本方案针对当前 Docker 日志搜索需求选用 OpenObserve，不同时部署两套日志平台。

## 3. 数据采集与字段

### 3.1 采集范围

第一阶段只采集 `jizhang-app`，通过容器名过滤，不采集 MySQL、Redis 或其他容器。保留 Docker 日志原始时间、标准输出/错误流、容器名和原始消息；增加 `service=jizhang-server`、`environment=prod`、主机标识。后续项目使用各自的 `service`、`environment`，避免靠不同的日志平台或仅靠容器名区分。

保留原始 `message`；对访问日志额外提取以下字段，供精确筛选和范围查询：

| 字段 | 来源或规则 | 示例 |
| --- | --- | --- |
| `log_type` | 匹配访问日志时标记 `http_access` | `http_access` |
| `method` | `HTTP` 后的请求方法 | `POST` |
| `path` | `request.getRequestURI()`，不包含 query string | `/api/v1/transactions` |
| `status` | `status=` 后的 HTTP 状态码，转为整数 | `400` |
| `duration_ms` | `durationMs=` 后的耗时，转为数值 | `23` |
| `trace_id` | 日志前缀的 `traceId=...`；缺失或为 `-` 时不填 | `abc123` |

解析只针对现有的 `HTTP {method} {path} status={status} durationMs={duration}` 行；`Unhandled server exception` 等业务/异常日志保留原文，不能误标成访问日志。规则须先用实际容器日志样本验证，避免把 Spring Boot 日志前缀、日志换行或堆栈信息错误解析为路径。`traceId` 在当前日志文本里，解析成功后才可作为独立字段过滤；解析失败时仍可搜索原文。

`path` 是请求的实际 URI，不是 Spring MVC 路由模板。带 ID 的接口（如 `/api/v1/transactions/123`）需要按路径前缀查询；若以后需要按 `/api/v1/transactions/{id}` 统一聚合，再专门增加路由模板字段，不用猜测或截断路径。

### 3.2 查询方式

下面的条件是预期的**字段组合**，不是依赖具体版本的查询语法：

| 场景 | 查询条件 |
| --- | --- |
| 查指定接口 | `service=jizhang-server`、时间范围、`method=POST`、`path=/api/v1/transactions` |
| 查该接口失败请求 | 上述条件加 `status>=400`；分别查看 `4xx` 与 `5xx` |
| 查成功请求 | 上述条件加 `200<=status<300` |
| 查具体报错 | 时间范围、`service`，对原始 `message` 搜索异常名或指定关键字 |
| 查同一次请求 | 从响应头取得 `X-Trace-Id`，按 `trace_id` 筛选；解析不成功则搜索 `traceId=...` |

访问日志目前统一以 `INFO` 输出，**不能用日志级别 `ERROR` 代替失败请求筛选**。普通业务异常可能只出现 `HTTP ... status=400`，未必有异常堆栈。查询日志内容也不等于可以查询未记录的 HTTP 请求体：生产环境继续保持 `HTTP_LOG_INCLUDE_BODY=false`。

### 3.3 “成功/失败”的定义

第一阶段按 HTTP 状态码定义：`2xx` 为成功，`4xx/5xx` 为失败；`3xx` 单独看待，不直接算业务成功。当前统一异常处理会将业务异常映射为相应 HTTP 状态，将参数错误映射为 `400`、未处理异常映射为 `500`。如果将来出现 **HTTP 200 但响应业务 `code` 非 `"0"`** 的情况，仅靠现有访问日志无法识别；若确有需求，第二阶段由应用输出不含响应体和敏感数据的业务结果码，再增加结构化字段 `business_code`，不通过开启响应体日志来实现。

## 4. 部署与可靠性

1. 日志服务使用独立 Compose，OpenObserve 数据目录挂载持久化卷；Vector 的配置及缓冲目录分别持久化。固定经验证的镜像版本，不使用不受控的 `latest`。
2. 同机部署时，OpenObserve UI 只绑定宿主机 `127.0.0.1`，Vector 通过独立 Compose 的内部网络发送，不要求应用加入该网络。远程查看优先使用 SSH 隧道或现有受控入口。
3. 给 `jizhang-app` 配置 Docker 本地日志轮转，例如每文件 20 MB、保留 5 个文件；Vector 设置有容量上限的磁盘缓冲。以上数值为起点，须按实际日志量和磁盘空间调整。
4. OpenObserve 日志留存期暂按 14 天设计。上线前测量日均写入量与存储占用，明确服务器可用空间、预警阈值及备份/恢复方式。日志服务与应用同机时，服务器故障会同时影响日志查询；不承诺零丢失或高可用。
5. 故障应急时仍可使用 `docker compose -f deploy/docker/docker-compose.app.yml logs --tail 200 app` 查看 Docker 本地尚未轮转的日志。

Docker 日志轮转、Vector 缓冲及 OpenObserve 留存是三个不同的期限和容量，不能相互替代。短暂停止 OpenObserve 后恢复应做补送测试；超过缓冲或 Docker 本地日志保留能力时可能丢失日志。

## 5. 安全要求

- 仅授权人员可访问查询界面；初期不将 OpenObserve 端口直接暴露公网。采集凭据保存在服务器私有配置中，不写入仓库、镜像或日志。
- Vector 若需要挂载 Docker socket，即使以只读方式挂载，也不能当成权限隔离手段；限定可运行采集器的主机与管理权限，并只采集指定容器。
- 保持 `HTTP_LOG_INCLUDE_BODY=false`，不主动采集 Authorization、Token、密码、财务明细和请求/响应体；对已有业务异常日志抽样检查敏感信息。禁止把日志搜索系统用作长期、不可删除的审计存档。
- 按项目筛选是查询便利功能，**不是访问授权**。若将来多个团队需互相隔离日志，需重新评估产品权限能力或物理隔离部署，再开放多团队使用。

## 6. 实施步骤与验收

| 步骤 | 工作 | 验收标准 |
| --- | --- | --- |
| 1 | 记录实际容器日志样本，确认 Docker 日志驱动、磁盘余量与留存需求 | 日志格式与解析规则对应；明确存储位置和备份责任 |
| 2 | 部署独立 OpenObserve + Vector，只采集 `jizhang-app` | 新日志可见，MySQL/Redis 日志未混入，重启后历史日志仍在 |
| 3 | 提取访问日志字段，保留原始消息 | 按 URL、请求方法、HTTP `2xx/4xx/5xx` 筛选；字段值与原始日志一致 |
| 4 | 执行受控请求验证 | 用一个成功请求和一个可控的 `4xx` 请求，按路径、状态、`X-Trace-Id` 找到对应日志；能搜索一条已输出的指定内容 |
| 5 | 检查可靠性和权限 | 短时停用并恢复 OpenObserve，核对补送；验证 UI 不对公网开放，轮转和缓冲有上限 |
| 6 | 如需接入其他项目再扩展 | 新项目与记账项目可按 `service`、`environment` 区分，不默认采集所有容器 |

如需验证 `5xx`，在测试环境制造受控异常；不在生产环境故意触发不可预期错误。正式实施前还需确认：实际是否同机部署、14 天留存是否足够，以及谁需要访问日志界面。
