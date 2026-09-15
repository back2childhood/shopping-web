# Shopping Web MVP

这是一个可通过 Docker Compose 本地运行的电商微服务面试项目。当前 MVP 刻意只保留账户、认证、商品、库存和订单主链路：用户注册或登录后，卖家进入商品管理页，买家进入购物页并创建订单。项目重点展示服务发现、网关鉴权、多存储选型、缓存和并发库存控制，而不是假装已经具备完整生产电商系统的全部能力。

## 当前状态

截至 2026-09-06，六个后端模块、前端和三种数据基础设施可以一起构建和启动。已经实际验证以下链路：AccountService 生成全局用户 ID，AuthService 用同一个 ID 保存认证记录并签发带角色的 JWT，卖家可以创建商品、买家不能创建商品，买家可以读取商品并通过 OrderService 的受认证内部调用预留库存和创建订单。

项目统一使用 Java 17、Maven 3.9.11 Wrapper、Spring Boot 3.5.7 和 Spring Cloud 2025.0.1。根 POM 只聚合当前 MVP 所需的六个模块，旧的 Cart、Payment、Notification 和 Common 代码仍保留在仓库中，但不参与本次构建或 Compose 启动。

## 架构与数据归属

```mermaid
flowchart LR
    Browser[React 前端 :3000] --> Gateway[API Gateway :8080]
    Gateway --> Auth[AuthService :8082]
    Gateway --> Account[AccountService :8081]
    Gateway --> Item[ItemService :8083]
    Gateway --> Order[OrderService :8084]
    Auth --> Account
    Order --> Item
    Auth --> PG[(PostgreSQL)]
    Account --> PG
    Item --> PG
    Order --> PG
    Item --> Cassandra[(Cassandra item_service)]
    Item --> Redis[(Redis)]
    Order --> Redis
    Gateway -. 服务发现 .-> Eureka[Eureka :8761]
```

数据拆分遵循“按访问与一致性需求选择存储”的原则。AuthService 在 PostgreSQL 保存登录凭据，AccountService 在独立 schema 保存用户资料，OrderService 在独立 schema 保存订单和不可变的商品快照。ItemService 将适合按 ID 高吞吐读取的商品目录保存到 Cassandra，但将会发生并发竞争的库存保存到 PostgreSQL；Redis 只缓存商品和订单响应，不作为事实来源。

把目录放入 Cassandra 并不是所有公司的固定做法。许多公司的商品主数据也会放在 PostgreSQL/MySQL，再通过搜索引擎、缓存和只读模型扩展读取能力。本项目使用 Cassandra 的价值是展示多存储设计；把库存单独放进 PostgreSQL，则避免用弱事务模型直接处理超卖问题。面试时应明确解释这个取舍，而不要声称 Cassandra 天然比关系数据库更适合所有商品数据。

## 微服务职责

| 服务 | 职责 | 主存储 | 缓存/依赖 |
| --- | --- | --- | --- |
| API Gateway | 统一入口、CORS、JWT 校验、按服务名转发 | 无 | Eureka |
| EurekaServer | 服务注册与发现 | 无 | 无 |
| AuthService | 注册、登录、BCrypt 密码散列、复用全局用户 ID、签发 JWT | PostgreSQL `auth_service` | AccountService、Eureka |
| AccountService | 保存不含密码的用户资料，并作为当前系统用户 ID 的生成方 | PostgreSQL `account_service` | Eureka |
| ItemService | 商品目录 CRUD、SELLER 写权限与库存预留/释放 | Cassandra `item_service`、PostgreSQL `inventory_service` | Redis、Eureka |
| OrderService | 创建订单、保存价格快照、查询用户订单 | PostgreSQL `order_service` | ItemService、Redis、Eureka |
| frontend | 登录/注册、按角色分流、卖家商品管理、买家购物与订单历史 | 浏览器状态 | API Gateway |

### 请求链路

注册时，AuthService 通过 OpenFeign 同步调用 AccountService 创建公开资料，AccountService 的 PostgreSQL identity 生成当前系统的全局 `userId`。AuthService 随后用这个 ID 保存密码散列和角色，因此两个服务不会各自生成互不相干的用户 ID；AccountService 按规范化邮箱幂等返回已有账户，使网络重试能够复用同一 ID。注册和登录响应包含 JWT、`userId`、`email`、`role` 和 `isSeller`，前端因此可以立即显示用户信息，并把卖家送到管理视图、把普通用户送到购物视图。

JWT 的 `sub` 是全局 `userId`，并包含 `id`、`email` 和 `roles` claims；当前角色为 `SELLER` 或 `BUYER`。ItemService 会自行校验 JWT 的签名和过期时间，并在服务端限制商品 create/update/delete 只能由 SELLER 执行。库存预留与释放只接受 OrderService 通过 OpenFeign 附带的内部服务凭证，普通用户 token 不能直接调用库存写接口；这个共享凭证是本地 MVP 的过渡方案，生产环境应替换为 mTLS、短期 service token 或 OAuth2 client credentials。

下单时，OrderService 不信任浏览器提交的价格，而是向 ItemService 读取商品名称、价格和币种。ItemService 用一条带 `available_quantity >= quantity` 条件的 PostgreSQL 更新语句预留库存，因此两个请求并发购买最后一件商品时，只有一个更新能够成功。OrderService 随后保存订单及商品快照，并把查询响应缓存到 Redis；如果保存阶段抛出异常，会尽力调用 ItemService 释放已经预留的库存。

## 数据模型

开发环境由 Hibernate 创建业务表，Compose 初始化脚本负责创建 PostgreSQL schemas 和 Cassandra keyspace。下表是当前实体对应的逻辑模型。

### PostgreSQL

`auth_service.users`

| 字段 | 类型/约束 | 用途 |
| --- | --- | --- |
| `id` | bigint, PK | 与 AccountService 相同的全局用户 ID，不在 AuthService 再次生成 |
| `email` | varchar, NOT NULL, UNIQUE | 登录名 |
| `password` | varchar, NOT NULL | BCrypt hash，不保存明文 |
| `is_seller` | boolean, NOT NULL | 前端角色分流依据 |

`account_service.accounts`

| 字段 | 类型/约束 | 用途 |
| --- | --- | --- |
| `id` | bigint, PK, identity | 账户资料 ID |
| `email` | varchar, NOT NULL, UNIQUE | 与认证账户关联的邮箱 |
| `username` | varchar, NOT NULL | 展示名称 |
| `shipping_address` | varchar, nullable | 收货地址 |
| `billing_address` | varchar, nullable | 账单地址 |
| `seller` | boolean, NOT NULL | 是否卖家 |
| `created_at` | timestamp | 创建时间 |

`inventory_service.inventory`

| 字段 | 类型/约束 | 用途 |
| --- | --- | --- |
| `item_id` | varchar, PK | 对应 Cassandra 商品 UUID |
| `available_quantity` | integer, NOT NULL | 当前可售库存 |
| `version` | bigint, NOT NULL | 每次库存变化递增 |

`order_service.orders`

| 字段 | 类型/约束 | 用途 |
| --- | --- | --- |
| `id` | bigint, PK, identity | 订单 ID |
| `user_id` | bigint, NOT NULL | 下单用户 |
| `total_price` | numeric(19,2), NOT NULL | 服务端计算的总价 |
| `currency` | varchar(3), NOT NULL | 订单币种 |
| `status` | varchar, NOT NULL | 当前为 `PENDING` |
| `created_at` | timestamptz, NOT NULL | 创建时间 |

`order_service.order_items`

| 字段 | 类型/约束 | 用途 |
| --- | --- | --- |
| `id` | bigint, PK, identity | 明细 ID |
| `order_id` | bigint, FK | 所属订单 |
| `item_id` | varchar, NOT NULL | 商品 ID 快照 |
| `item_name` | varchar, NOT NULL | 商品名称快照 |
| `quantity` | integer, NOT NULL | 数量 |
| `price` | numeric(19,2), NOT NULL | 下单时单价快照 |

同一订单的 `(order_id, item_id)` 有唯一约束。价格和名称保存在订单明细中，保证卖家后来修改商品时，历史订单仍能显示下单时信息。

### Cassandra

`item_service.items_by_id`

| 字段 | 类型 | 用途 |
| --- | --- | --- |
| `id` | text, partition key | 商品 UUID |
| `name` | text | 名称 |
| `description` | text | 描述 |
| `price` | decimal | 展示价格 |
| `currency` | text | ISO 风格三字母币种 |
| `created_at` | timestamp | 创建时间 |
| `updated_at` | timestamp | 更新时间 |

当前查询模型只有“按 ID 查询”和“小数据量列出全部商品”。后者在 Cassandra 大数据量下不合适，未来应新增按分类或分页访问模式设计的反规范化表，而不是依赖全表读取。

## HTTP API

所有客户端请求都通过 `http://localhost:8080` 进入网关。除 `/api/auth/**` 外，其余路由需要 `Authorization: Bearer <token>`。

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| POST | `/api/auth/register` | 注册并返回 token、userId、email、role、isSeller |
| POST | `/api/auth/login` | 登录并返回 token、userId、email、role、isSeller |
| POST | `/api/accounts` | 创建账户资料，通常由 AuthService 调用 |
| GET | `/api/accounts/{id}` | 查询账户资料 |
| GET | `/api/accounts` | 查询全部账户 |
| POST | `/api/items` | SELLER 创建商品及初始库存 |
| GET | `/api/items/{id}` | 查询商品与当前库存 |
| GET | `/api/items` | 查询商品列表 |
| PUT | `/api/items/{id}` | SELLER 更新商品及库存 |
| DELETE | `/api/items/{id}` | SELLER 删除商品及库存 |
| POST | `/api/items/{id}/decrease-stock` | OrderService 内部调用，原子预留库存 |
| POST | `/api/items/{id}/increase-stock` | OrderService 内部调用，释放库存 |
| POST | `/api/orders` | 创建订单 |
| GET | `/api/orders/{orderId}` | 查询订单，可命中 Redis |
| GET | `/api/orders/user/{userId}` | 查询用户订单 |

## 本地运行

前提是已安装并启动 Docker Desktop。项目不要求本机安装 Java、Maven、PostgreSQL、Redis 或 Cassandra；每个服务都由自己的多阶段 Dockerfile 构建，Maven Wrapper 保证使用仓库指定的 Maven 版本。

```bash
docker compose -f compose.yml up --build
```

首次构建需要下载 Maven、npm 和 Docker 镜像依赖，也需要等待 Cassandra 健康检查完成，因此会比之后启动慢。Compose 会按 PostgreSQL、Redis、Cassandra、Eureka、业务服务、网关和前端的就绪状态依次启动。

启动后可访问：

- 前端：<http://localhost:3000>
- API Gateway：<http://localhost:8080>
- Eureka 控制台：<http://localhost:8761>
- 网关健康检查：<http://localhost:8080/actuator/health>

如果本机 8080 已被其他项目占用，可以只改变网关的宿主机端口；前端构建时会自动使用同一个端口。

```bash
GATEWAY_PORT=18080 docker compose -f compose.yml up --build
```

查看状态和停止项目：

```bash
docker compose -f compose.yml ps
docker compose -f compose.yml down
```

`down` 不会删除命名数据卷。只有明确想清空本地 PostgreSQL、Redis 和 Cassandra 数据时才使用 `docker compose -f compose.yml down -v`。

## 不使用 Docker 的构建方式

macOS/Linux 使用：

```bash
./mvnw test
```

Windows 使用：

```powershell
mvnw.cmd test
```

Maven Wrapper 是随仓库提交的启动脚本和版本配置。它不是另一套构建系统；它会在第一次运行时取得项目指定的 Maven 3.9.11，然后仍然执行普通 Maven 生命周期。这样团队和 CI 不会因为各自安装的 Maven 版本不同而得到不一致结果。

项目以 Java 17 为目标版本；父 POM 已显式配置 Lombok annotation processor，因此在不再自动发现处理器的较新 JDK 上也能编译。正式开发和 CI 仍建议使用 Java 17，以保持与容器运行时完全一致。

前端位于 `frontend`，使用 Node.js 22 构建。单独开发时可执行：

```bash
cd frontend
npm ci
npm run dev
```

## 已验证内容

- Maven 六模块 reactor 测试通过；新增测试覆盖全局 ID 复用、JWT claims、SELLER 角色解析和 OrderService 内部身份解析。
- 前端 lint 和生产构建通过。
- 七个应用镜像均成功构建。
- PostgreSQL、Redis、Cassandra 与 Eureka 健康检查通过。
- 前端和网关健康接口返回 HTTP 200。
- 已通过数据库联表验证 AccountService 与 AuthService 的用户 ID、邮箱和角色一致。
- 卖家创建商品返回 HTTP 200，买家创建商品返回 HTTP 403，买家读取商品与创建订单返回 HTTP 200。
- 同一商品列表和同一订单连续读取两次成功；已修复 Redis 的旧缓存隔离，以及订单对 `Instant` 和 `BigDecimal` 的序列化/反序列化问题。
- 下单后 PostgreSQL 库存按请求数量扣减；条件更新防止库存变成负数。

## TODO 与已知限制

这些项目是当前代码真实存在的边界，也是后续最适合按阶段推进的内容。

### 高优先级：身份与授权

- ItemService 已在服务端执行 SELLER 写权限检查，但网关目前只校验 JWT 的签名和过期时间，还没有强制校验 `issuer=auth-server` 与 `audience=api-client`。issuer 可以阻止其他环境或其他认证系统签发的 token 被误用，audience 可以阻止原本签给另一个客户端或 API 的 token 被本系统接受；这是防止 token confusion 和跨环境误用的纵深防御，按当前迭代决定暂留 TODO。
- OrderService 仍接受请求体中的 `userId`，攻击者可以替其他用户下单或读取 `/api/orders/user/{userId}`。下一步应让网关移除任何客户端伪造的身份头，从已验证 JWT 提取 `sub` 和角色写入内部可信 header，并让 OrderService 只使用该身份。
- AccountService 的创建和列表接口尚未区分内部调用与普通用户，业务服务端口也映射到宿主机。生产部署应只公开网关，并对内部路由采用网络策略和服务身份验证。
- OrderService 到 ItemService 当前使用本地共享服务凭证，能防止浏览器直接调用库存接口，但不能作为完整生产服务身份方案。部署时应改用 mTLS、短期签名 service token 或 OAuth2 client credentials，并由 secret manager 管理凭证。
- 默认 JWT secret 和内部服务凭证只适合本地演示。部署时必须通过安全环境变量或 secret manager 提供独立高强度密钥，并增加 access token 短过期、refresh token 和撤销策略。

### 高优先级：跨服务一致性

- PostgreSQL 的原子条件更新解决了“多用户购买最后一件商品”的超卖问题，但库存预留和订单写入仍属于两个服务的两个本地事务。当前异常路径会同步补偿库存，却无法覆盖进程在两次调用之间崩溃、网络超时后结果未知、补偿调用失败等情况。
- 下一阶段应给库存预留增加 `reservationId` 和幂等约束，建立 `RESERVED / CONFIRMED / RELEASED / EXPIRED` 状态与过期释放机制。OrderService 可采用 transactional outbox 发布订单事件，再由 Saga 协调确认或释放库存。
- AuthService 先同步创建账户资料，再写入本地认证记录；AccountService 已按邮箱提供重试幂等性，但 AuthService 在后续写入前崩溃仍会留下没有凭据的账户资料，并发同邮箱请求也仍需处理唯一约束冲突。后续应加入注册幂等键、明确的 `PENDING/ACTIVE` 注册状态和 reconciliation，不应为了面试展示而直接加入尚未正确使用的 Kafka。

### 商品与缓存

- 商品目录和 PostgreSQL 库存的创建、更新、删除不是跨数据库原子事务。需要用 outbox、可重试同步任务或 reconciliation job 检测并修复孤立记录。
- 商品列表当前调用 Cassandra `findAll()`，只适合 MVP。真实目录应根据分类、卖家、更新时间或搜索需求设计查询表，并使用 OpenSearch/Elasticsearch 承担文本搜索。
- Redis 采用 cache-aside，数据库是事实来源。仍需加入 TTL、缓存指标、热点 key 和击穿保护，并为缓存不可用设计降级；Redis 故障不应让核心写入返回失败。
- 库存接口使用 `Map<String,Integer>` 接收数量，后续应改成带 Bean Validation 的 DTO，并统一 400、404、409 错误响应格式。

### 订单能力

- 当前只有创建和查询，状态固定为 `PENDING`，尚无支付、取消、发货、退款和状态机。
- 用户订单查询没有分页；订单量增长后必须增加 `(user_id, created_at)` 索引并使用稳定游标或分页。
- 同一商品在一个订单请求中重复出现时会违反订单明细唯一约束；API 应在校验阶段合并重复商品或明确拒绝。
- 需要加入请求幂等键，防止客户端超时重试创建重复订单。

### 工程质量与运维

- 当前测试主要是上下文和少量单元测试；应增加 Testcontainers 集成测试以及覆盖注册、授权、库存竞争、补偿和缓存故障的端到端测试。
- Hibernate 使用自动建表适合本地 MVP，不适合生产。应使用 Flyway/Liquibase 管理 PostgreSQL migration，并用版本化 CQL migration 管理 Cassandra。
- 需要统一结构化日志、trace ID、Micrometer 指标、OpenTelemetry tracing、超时、重试和 circuit breaker。重试只能用于幂等操作，不能盲目重试下单。
- 前端依赖审计目前报告 13 个依赖链漏洞，其中 10 个为 high。升级前应检查脚手架及 Vinext 兼容性，不能直接使用破坏性自动修复。
- 旧的 CartService、PaymentService、NotificationService 和 Common 模块尚未迁移到当前架构。它们不在父 POM 和 Compose 中，后续应逐个评估、重写并测试，而不是直接重新启用。

## 推荐的下一步顺序

下一步应把订单身份改为只信任经过验证的 JWT `sub`，不能继续相信请求体中的 `userId`；同一阶段可以补上网关的可信身份 header 和下游 header 防伪。随后为下单加入幂等 key 和库存 reservation 模型，并编写并发测试证明不会超卖。再下一阶段加入支付和取消状态机；只有事件契约、幂等、outbox 和失败恢复方案明确后，才引入 Kafka。这个顺序能让项目在每个阶段都可运行、可解释，也更符合真实工程环境。
