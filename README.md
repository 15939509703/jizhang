# 记账小程序后端

基于 Java 21、Spring Boot、MyBatis-Plus、MySQL 和 Redis 的模块化后端工程。

## 版本管理

后端使用 GitHub 仓库的独立 `dev` 分支，与小程序 `mini-program` 分支互不共享文件历史。

只克隆后端代码：

```bash
git clone --single-branch --branch dev \
  git@github.com:15939509703/jizhang.git jizhang-server
```

进入后端目录后，正常执行 `git pull` 只会更新 `dev` 分支。

## 当前功能

- 微信小程序`code2session`真实登录。
- 用户及微信身份自动创建。
- 首次登录自动创建个人账本、现金账户和用户分类。
- JWT Access Token和Redis Refresh Token会话。
- 统一响应、参数校验、异常处理和链路追踪。
- 账本列表与创建，自动初始化分类和现金账户。
- 分类列表、账户列表与账户创建。
- 账单新增、游标分页查询和业务作废。
- 账单请求幂等、账户行锁、余额分录和作废冲正。
- IDEA控制台HTTP访问日志。

## 前置条件

1. MySQL中已执行`../记账小程序_数据库初始化.sql`。
2. MySQL中已执行`../记账小程序_基础数据初始化.sql`。
3. Redis运行在本机6379端口。
4. 已在微信公众平台取得真实小程序AppID和AppSecret。

## 环境变量

```bash
export JAVA_HOME=/Users/lhj/Library/Java/JavaVirtualMachines/ms-21.0.7/Contents/Home
export DB_USERNAME=root
export DB_PASSWORD='本机数据库密码'
export WX_APP_ID='真实小程序AppID'
export WX_APP_SECRET='真实小程序AppSecret'
export JWT_SECRET_BASE64="$(openssl rand -base64 32)"
```

本机开发配置保存在被Git忽略且权限为`600`的`.env.local`。在IDEA终端可直接运行：

```bash
./run-local.sh
```

新环境可参考`.env.example`创建自己的`.env.local`，不要提交真实AppSecret。

## 编译与启动

工程要求使用JDK 21，根目录的`.java-version`和Maven Enforcer会阻止误用其他JDK。

本机Maven位于IntelliJ IDEA安装目录：

```bash
MAVEN="/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn"
"$MAVEN" clean test
"$MAVEN" -pl jizhang-bootstrap -am spring-boot:run
```

服务默认启动在`http://127.0.0.1:8081`，健康检查地址为：

```text
GET http://127.0.0.1:8081/actuator/health
```

Swagger UI和OpenAPI文档地址：

```text
http://127.0.0.1:8081/swagger-ui.html
http://127.0.0.1:8081/v3/api-docs
```

调用受保护接口时，在Swagger UI的`Authorize`中填写JWT Access Token。

## 登录接口

```http
POST /api/v1/auth/wechat/login
Content-Type: application/json

{
  "code": "wx.login返回的临时code",
  "nickName": "用户昵称",
  "avatarUrl": "头像地址"
}
```

接口不会信任客户端传入的openid。后端使用AppID、AppSecret和临时code向微信服务端换取openid，再创建或查询用户。

## 业务接口

除微信登录和健康检查外，接口都需要携带请求头：

```http
Authorization: Bearer <accessToken>
```

当前已实现：

```text
GET  /api/v1/users/me
PUT  /api/v1/users/me
GET  /api/v1/books
POST /api/v1/books
PUT  /api/v1/books/{id}
GET  /api/v1/books/{id}/members
POST /api/v1/books/{id}/members
PUT  /api/v1/books/{bookId}/members/{memberId}
DELETE /api/v1/books/{bookId}/members/{memberId}
GET  /api/v1/categories?bookId={bookId}&type={EXPENSE|INCOME}&includeHidden={boolean}
POST /api/v1/categories
PUT  /api/v1/categories/{id}
PUT  /api/v1/categories/{id}/visibility
PUT  /api/v1/categories/sort
DELETE /api/v1/categories/{id}
GET  /api/v1/accounts?bookId={bookId}
POST /api/v1/accounts
GET  /api/v1/accounts/{id}
GET  /api/v1/accounts/{id}/entries
POST /api/v1/accounts/{id}/adjustments
GET  /api/v1/transactions?bookId={bookId}&cursorId={cursorId}&limit=20
GET  /api/v1/transactions/{id}
PUT  /api/v1/transactions/{id}
GET  /api/v1/transactions/summary?bookId={bookId}&month={YYYY-MM}
POST /api/v1/transactions
POST /api/v1/transactions/{id}/void
POST /api/v1/transactions/{id}/attachments
GET  /api/v1/attachments/{id}
DELETE /api/v1/attachments/{id}
GET  /api/v1/statistics?bookId={bookId}&month={YYYY-MM}&year={YYYY}
GET  /api/v1/budgets?bookId={bookId}&month={YYYY-MM}
POST /api/v1/budgets
```

新增支出示例：

```http
POST /api/v1/transactions
Content-Type: application/json
Authorization: Bearer <accessToken>

{
  "requestId": "客户端生成的全局唯一请求号",
  "bookId": 1,
  "transactionType": "EXPENSE",
  "categoryId": 1,
  "accountId": 1,
  "amount": 28.50,
  "happenedAt": "2026-07-21T10:00:00Z",
  "title": "午餐",
  "note": "工作餐"
}
```

转账时使用`TRANSFER`，不传`categoryId`，并通过`targetAccountId`指定转入账户。

## 小程序配置

开发者工具中需将`jizhang-mini-program/project.config.json`的`appid`由`touristappid`替换为与`WX_APP_ID`相同的真实AppID。真机调试时，API地址需改为已备案的HTTPS域名，不能使用`127.0.0.1`。

本地微信开发者工具模拟器默认请求`http://127.0.0.1:8081`，配置位于`jizhang-mini-program/utils/request.js`。

账单图片默认保存到后端运行目录的`data/uploads`，生产环境应通过`UPLOAD_DIR`配置持久化目录。
