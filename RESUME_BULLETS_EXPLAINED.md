# 电商项目简历逐条解释与英文改写

这份文档先解释每条简历背后的业务，再给出容易讲清楚的英文表达。技术方案默认讨论完整目标版本；它不是当前仓库的功能验收报告。最后的英文条目是该版本的写作模板，不能单凭模板证明某项功能已经上线，数字示例也不代表实际压测结果。

简历不需要把所有实现细节挤进一句话。简历写清楚“做了什么、解决什么问题”，面试时再解释表结构、失败场景和技术取舍。下面的 SQL 都是说明思路的示例，不是本次新增的项目代码。

最新阅读入口：从[第 14 节](#14-最新版简历的-14-条完整讲解)开始，按你最新提供的 14 条英文 bullet 原顺序逐条学习。每条包含完整原文、职责含义、技术分工与流程、异常处理、价值验证和英文回答。第 1–13 节保留为术语和实现细节参考；第 14 节的条目顺序优先于第 12 节的旧版编排。

## 1. PostgreSQL 如何避免超卖和重复扣库存

### 先分清两个问题

超卖是两个人同时争抢最后一件商品，结果两人都买到了。重复扣库存是同一个人的一次购买，因为网络重试而扣了两次库存。前者用数据库的条件更新解决，后者需要给购买操作一个稳定的身份，不能混为一谈。

例如商品 A 只剩一件，两个请求都想买一件。不能先在 Java 中查到库存为 1，然后都决定扣减。应该让 PostgreSQL 在同一条语句里检查库存和扣减库存，只有更新成功的请求才拿到这一件：

```sql
UPDATE inventory
SET available_quantity = available_quantity - :quantity
WHERE item_id = :itemId
  AND available_quantity >= :quantity;
```

业务层先验证 quantity 大于零。更新一行表示成功，更新零行表示商品不存在或库存不足，需要按业务区分处理。这个原子操作防止超卖，但重复调用仍然可能连续成功，所以还需要预留记录。

### 唯一预留标识具体怎么用

假设订单 1001 要买商品 A 两件，可以用 `(orderId=1001, itemId=A)` 标识这次预留，并在 reservation 表建立唯一约束。同一订单里重复的商品行先合并。请求重试时必须沿用原来的标识，而不是重新生成一个，否则数据库会认为这是另一笔购买。

在一个本地事务里，先用唯一约束尝试建立预留记录，再执行库存扣减；库存不足就让整个事务回滚。可以使用 `INSERT ... ON CONFLICT DO NOTHING` 判断是否是第一次请求；已存在时检查数量和状态，返回原结果或拒绝冲突，不再扣库存。不要简单依赖“先 SELECT，没有再 INSERT”，因为两个请求可能同时查到没有记录。

确认和释放也必须检查原状态。释放时仅允许 RESERVED 变为 RELEASED，并且只有成功改变这条状态的事务才把数量加回去。更新状态和恢复库存属于同一个事务；第二次释放发现已经 RELEASED，只返回已有结果。确认则把 RESERVED 变为 CONFIRMED，不能再作为普通未付款预留释放。

这意味着“唯一 ID”本身不是保护措施。真正起作用的是稳定 ID、数据库唯一约束、条件状态更新和本地事务共同配合。这里假设一个订单商品只有一次预留；如果允许释放后重新预留，要增加明确的预留尝试编号。

### 面试时可以这样说

“I let PostgreSQL check and update stock in one operation. I also keep a reservation record for each order item, so retrying the same request does not deduct stock again. Releasing a reservation checks its previous state, which prevents adding stock back twice.”

## 2. 支付幂等到底是什么意思

幂等在这里就是：用户多点几次付款，或者网络导致请求重发，也只应完成一次业务扣款。假设订单是 100 美元，第一次请求实际上扣款成功，但返回途中断网，浏览器不知道结果，因此再次发送请求。第二次请求必须找到第一次的付款操作，而不是再创建一笔 100 美元扣款。

原来的句子一次塞了四个机制，读起来确实太重。它们分别解决以下问题：

| 机制 | 简单含义 | 本项目中的例子 |
|---|---|---|
| Idempotency-Key | 为同一次操作提供固定请求编号 | 两次点击重发都带 pay-request-123 |
| Request fingerprint | 记录关键请求内容，以识别同编号不同内容 | 比较 orderId、amount、currency 的规范化内容或哈希 |
| 数据库唯一约束 | 多个实例同时收到请求时，只建立一条身份记录 | 用户、操作类型、请求编号的组合唯一 |
| Provider request ID | 调第三方支付也使用固定操作编号 | 超时重试仍使用同一 paymentAttemptId |

如果同一编号第一次对应 100 美元，第二次却对应 200 美元，不能假装它们是同一个请求，需要拒绝冲突。服务端应根据可信订单计算付款金额，不接受前端任意指定金额。除了相同请求编号的去重，还要限制同一订单不能同时启动两笔未解决的支付，否则用户换一个编号仍可能重复扣款。

数据库能限制本地记录，却不能保证外部支付平台没有扣款。因此第三方必须支持幂等编号或可靠的状态查询。发生超时时，把支付保留为处理中，通过查询或回调确认结果；不能把“没收到响应”理解成“没有扣款”。这就是 reconciliation，在这里可以直接叫“查询并核对支付结果”，不必背抽象术语。

### 面试时可以这样说

“I assign one payment reference to each payment attempt. When the same request is retried, the service returns the existing result instead of creating another charge. If the provider times out, I check the original payment status before deciding what to do next.”

## 3. Saga 是什么，和 Outbox 有什么不同

一个订单可能需要 Order Service 保存订单、Item Service 预留库存、Payment Service 扣款。这三个服务分别管理自己的数据库，没有一个普通的 Spring 本地事务可以同时回滚三边的操作。Saga 就是把这个大流程拆成几个本地步骤，并为已经成功的步骤定义后续失败时如何补救。

例如库存已预留，但支付明确失败，就需要释放库存。如果支付已经成功，随后业务决定取消，则需要发起退款；这不是把 PostgreSQL 事务 rollback 一下就能完成的。释放和退款都是新的业务操作，也可能失败，所以要保存进度并允许重试。

在本项目的目标流程里，Order Service 可以记录当前进行到哪一步、上一次结果和下一步动作，扮演协调者。服务重启后根据这些记录继续处理，而不是依靠内存中的 try/catch。所谓 durable Saga coordination，就是“把订单处理进度保存下来，失败后可以继续或补救”。

Outbox 解决的是另一件事：数据库已保存的变化不能因为 Kafka 暂时不可用而丢掉通知。业务记录和待发送事件在同一个本地事务中提交，publisher 再发送事件。Outbox 保证留下发送依据，Saga 决定收到结果后下一步做什么，两个机制不是同义词。

### 面试时可以这样说

“I track the order workflow in the database. If payment fails after stock is reserved, the workflow releases the stock. If a recovery step fails, the system keeps it pending and retries it instead of losing the work when the service restarts.”

## 4. Kafka 去重、重试、死信和重放分别由谁实现

Kafka 负责存储和传送消息，不会自动判断你的订单是否已经扣过款。消费者完成数据库更新后、提交消费进度之前宕机，同一条消息可能再次被处理。因此这里的可靠性能力主要由消费者业务代码、数据库和 Spring Kafka 的错误处理配置共同完成，不能只写“Kafka 保证不重复”。[Kafka 官方说明](https://kafka.apache.org/design/)

### 去重：记住这个消费者已经处理过哪些事件

给每条业务事件一个稳定的 eventId。消费者在自己的 PostgreSQL 中维护 processed_event 表，使用 `(consumerName, eventId)` 唯一约束；“保存处理标记”和“更新业务数据”放在同一个事务。第一次成功提交后，重复消息看到已有记录就跳过对应业务效果；业务更新失败时，处理标记也回滚，之后可以重试。

这个办法只保证同一个数据库内的效果。如果消费者直接调用外部支付，处理标记不能和外部扣款形成一个本地事务，仍然需要上一节的支付操作编号。去重记录的保留时间也要覆盖允许的消息重放范围，不能很早删掉以后又宣称任意历史消息都不会重复执行。

### 有限重试：暂时失败可以再试，但不能永远卡住

例如数据库短暂不可用，可以等待后再试。假设设定总共三次尝试，就是首次处理加两次重试，不要把“retry=3”和“总共三次”混用。非法消息字段不会因为多试几次自动变正确，应该与短暂网络错误分开处理。

Spring Kafka 可以通过错误处理器和 backoff 配置安排失败处理。无限重试会让坏消息一直阻塞消费，完全不重试又会让短暂故障变成业务缺失，所以需要按错误类别决定次数、等待时间和最终去向。

### 死信：把无法处理的消息保留下来调查

超过重试策略后，把事件送到专门的失败 topic，例如 `order-events.DLT`，保留原 eventId、来源以及错误原因，并发出告警。必须确认失败消息可靠保存后才能按策略推进原消息进度，否则可能丢失它。死信不是垃圾桶，进去的订单仍需要被跟踪和处理。

### 受控重放：修复原因后，有选择地重新处理

例如修复了消息格式兼容问题后，运维人员先检查受影响消息，再限速重发，并保留原 eventId。消费者继续执行去重和状态检查，避免已经完成的订单再次执行。不要把整个 topic 随意从头重跑，更不要把原 eventId 换成新值来绕过去重。

### orderId 作为 key 能保证什么

稳定分区映射下，同一 topic 中相同 orderId 的消息进入同一分区。不同 topic 之间没有共同的顺序保证，而且把失败消息移到 retry topic 后，后续消息可能先被处理。订单消费者仍然要检查当前状态，不能因为消息到了就无条件把 CANCELLED 改成 PAID。

关于当前代码的定位：Item Service 的 EventProducer 使用 aggregateId 作为 key，OutboxPublisher 重试发送，EventConsumer 根据商品版本跳过部分旧的商品更新。这不等于完整的订单消费者去重、DLT 和重放流程；商品版本检查也不是覆盖所有删除和并发场景的通用去重机制。这里补充的是完整订单方案的实现解释。

### 面试时可以这样说

“I record processed event IDs to avoid applying the same database change twice. Temporary failures are retried, while messages that still fail are moved to a separate topic for investigation and replay after the issue is fixed.”

## 5. Negative caching 和 request coalescing 是什么

Negative caching 就是短暂缓存“查不到”的结果。假设大量请求查询不存在的商品 999，Redis 每次都没命中，服务就每次查数据库。如果把“商品不存在”保存 30 秒，这段时间重复查询就不必继续打数据库。30 秒只是例子；商品新建后需要删除对应的不存在标记，或者接受很短的可见性延迟。

不要把数据库超时也缓存成“商品不存在”。超时是无法确认结果，而不存在是已确认的结果，这两者必须区分。也不能把某个用户无权查看的结果缓存成所有用户都不可查看，缓存键和结果要遵守权限范围。

Request coalescing 是把“同时查询同一个商品”的请求合并为一次数据加载。比如热门商品缓存刚过期，100 个请求一起到达，让其中一个请求负责查询，其余请求等待同一个结果，而不是启动 100 次相同查询。加载成功后填缓存并唤醒等待者，加载失败也要让等待结束并清理这次进行中的任务。

Java 中可以用按商品 ID 管理的 CompletableFuture 表示进行中的加载，但必须限制等待时间、任务数量并清理完成的任务。单个 JVM 的合并只能覆盖这个实例；三个实例仍可能分别查一次。它主要保护数据源，不是让库存变得强一致，也不等于每个请求都更快。

### 面试时可以这样说

“I briefly cache not-found results and combine simultaneous requests for the same product into one data load. This reduces repeated database reads when an item is missing or a popular cache entry expires.”

## 6. Circuit breaker、bulkhead 和支付结果核对

Circuit breaker 是熔断器。支付接口连续失败或变慢达到阈值后，暂时停止继续调用它，稍后放少量请求检查是否恢复。它避免所有用户请求都去等待一个明显不健康的下游，但不会替你取消已经发生的扣款。

Bulkhead 可以直接理解为隔离并发资源。例如只允许 20 个支付请求同时访问某个提供方，其余请求按策略短暂等待或快速失败，避免支付故障占满整个应用的连接和执行资源。WebClient 是异步的也仍然需要并发上限；隔离并不一定要靠单独线程池实现。20 只是说明概念的例子，配置需要看吞吐和延迟。

如果一个支付请求已经发出后超时，不能立即返回“支付失败，请重新付款”。应该保存原 paymentAttemptId，告诉用户仍在确认，再通过查询或回调得到最终结果。若连请求都没有可靠保存，就不能假装已受理。Fallback 的责任是返回真实的可用状态，不是返回一个假的成功结果。

### 面试时可以这样说

“I limit how long payment calls can wait and how many can run at once. When the provider is unhealthy, the service temporarily stops sending more calls. Payments with an unknown result stay pending until their original status is confirmed.”

## 7. Spring Security 在请求里做什么，refresh token 为什么轮换

Spring Security 在请求到达业务 Controller 之前执行一组安全过滤器。JWT 验证逻辑检查签名、有效期和预期的签发者等信息，验证成功后建立当前用户身份。业务权限判断使用这个经过验证的身份，不是直接相信前端提交的 userId，也不是只把 JWT 解码就算验证成功。

在购物项目中，BUYER 可以下单，SELLER 可以管理商品，这是角色权限。但 SELLER 也不能修改另一个 SELLER 的商品，所以服务还要比较 token 中的用户 ID 和商品所有者。类似地，查询订单时应检查订单是否属于当前用户。角色判断回答“你能不能做这种操作”，ownership 回答“你能不能对这条数据做这个操作”。

Access token 用于访问普通 API，通常有效期较短。Refresh token 用于申请新的 access token，有效期通常更长，需要有可撤销的会话记录。Rotation 就是每次成功刷新时，旧 refresh token 被消费并失效，同时生成一个新的 refresh token，而不是一直复用同一个长期凭证。

例如浏览器拿 R1 刷新，服务在事务中验证并消费 R1，签发 A2 和 R2。R1 再次出现时可能意味着泄露，也可能是浏览器并发刷新，需要有明确策略和前端协调。长期令牌通常只保存安全哈希，退出登录可以撤销对应会话；已有 access token 是否立即失效还取决于过期时间和额外撤销机制。不要声称 rotation 自动撤销全部已签发 JWT。

如果 refresh token 放在 HttpOnly、Secure cookie 中，可以减少 JavaScript 直接读取它的机会，但 cookie 相关接口仍需考虑 CSRF。浏览器存储方式、刷新接口、撤销策略要一起设计，不是加两个 JWT 字段就完成了。

参考 [Spring Security 身份认证架构](https://docs.spring.io/spring-security/reference/servlet/authentication/architecture.html)和 [OAuth 安全规范中的 refresh token 保护](https://www.rfc-editor.org/rfc/rfc9700.html#section-4.14)。

### 面试时可以这样说

“I use Spring Security to validate login tokens and check user permissions. Buyers can access their own orders, and sellers can manage only their own products. Refreshing a session replaces the old refresh token so it cannot be reused normally.”

## 8. Kubernetes health probes 是什么

Probe 就是 Kubernetes 定期检查容器状态的方法。它可以请求一个 HTTP 健康检查地址，但不同 probe 回答的问题不同。Readiness 决定现在能不能给这个实例分配正常服务流量，liveness 决定容器是否需要重启，startup 则给启动慢的程序足够准备时间。[Kubernetes 官方说明](https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-probes/)

| 检查 | 购物服务里的含义 | 持续失败的主要结果 |
|---|---|---|
| Startup | Spring Boot 是否已完成启动 | 达到失败阈值后重启容器；成功前不执行 readiness/liveness |
| Readiness | 当前实例是否准备好接收请求 | 从常规 Service 可用端点中排除；本身不触发重启 |
| Liveness | 程序是否卡死且需要重启恢复 | 达到失败阈值后重启对应容器 |

例如新版本还在启动时，readiness 不通过，流量继续去已经就绪的实例。应用卡死时，liveness 连续失败后由 Kubernetes 重启。数据库暂时不可用不应该简单地让所有实例的 liveness 失败，因为重启所有应用通常不能修好数据库。

Spring Boot 的 Actuator 可以提供相应健康分组端点，常见路径是 `/actuator/health/readiness` 和 `/actuator/health/liveness`，但需要确认实际配置与暴露方式。不能因为容器显示 Running 就断言业务准备好了；探针也无法证明“支付一定正确”，仍然需要业务监控和测试。

### 面试时可以这样说

“I configure Kubernetes to send traffic only to instances that are ready. It also checks whether a container is stuck and needs a restart. During a rollout, new instances become available only after their readiness checks pass.”

## 9. Actuator、Micrometer、Prometheus 和 Grafana 分别做什么

这四个名字不是四套重复的监控。Actuator 给 Spring Boot 增加健康检查和运行信息接口，Micrometer 在应用里记录请求耗时、次数、JVM 等指标，Prometheus 定期采集并保存时间序列，Grafana 查询这些数据并显示图表。Spring Boot 提供 Micrometer 集成；配置 Prometheus registry 和受保护的暴露端点后，Prometheus 才能抓取对应数据。[Spring Boot 官方说明](https://docs.spring.io/spring-boot/reference/actuator/metrics.html)

例如你想知道创建订单是不是越来越慢，Micrometer 记录请求耗时，Prometheus 保存过去一段时间的数据，Grafana 展示 p95 延迟曲线。Actuator 的健康检查只回答某些健康条件，不会自动为你创建所有支付业务指标。支付成功率、待确认支付数量和最老 outbox 事件年龄，需要额外埋点；Kafka lag 也需要对应的消费者组指标采集。

指标告诉你问题出现在哪一段，日志帮助你调查具体订单。日志可以包含 traceId、orderId 和 eventId，但不能记录完整 token、密码或支付敏感信息。不要把每个 orderId 当成 Prometheus 指标标签，否则会产生大量独立时间序列，拖累监控系统。

### 面试时可以这样说

“I expose application health and metrics through Spring Boot Actuator and Micrometer. Prometheus collects the metrics, and Grafana shows request latency, errors, and JVM usage. I use trace IDs in logs to investigate individual failed requests.”

## 10. AI 商品推荐、Milvus、分块和 embedding

### 先说明这项功能是什么

用户不必只输入商品名称，可以说“我想买适合通勤、防水、100 美元以内的背包”，助手理解需求后找出相关商品，并解释推荐理由。用户还可以接着说“有没有更轻的”，助手结合聊天上下文重新搜索。这是基于商品内容的语义搜索和对话推荐，不自动等于使用购买历史训练的个性化推荐模型。

Embedding 是把文本转换成一组数字，使意思接近的文本在向量空间里也比较接近。Milvus 保存这些向量和必要的商品标识，提供相似度检索；它不是天然理解商品描述的语言模型。常见做法是应用先调用 embedding 模型生成向量，再连同 itemId、chunkId、文本或文本引用、内容版本等信息写入 Milvus。[Milvus 官方示例](https://milvus.io/docs/quickstart.md)

### 商品如何进入检索库

先取得商品名称、描述、类别和适合搜索的属性，再整理文本。描述很长时，按有意义的段落切成 chunks，每块生成 embedding，保留它属于哪件商品。短商品描述往往一件商品一个向量就够了，不应为了写“chunking”把几句话拆成很多没有上下文的小片段。

商品修改后更新对应向量，删除或下架后清理或过滤旧条目。需要跟踪内容版本和 embedding 模型版本，模型更换可能需要重建索引。Milvus 是检索索引，不取代商品数据库中的权威价格和库存。

### 用户聊天后发生什么

助手把自然语言需求转换为搜索文本和明确筛选条件，例如类别及价格上限。搜索文本使用与商品检索兼容的 embedding 模型编码，再到 Milvus 取回相关片段，按 itemId 合并同一商品的多个片段。之后调用商品服务核实当前商品状态、价格和库存，再把真实商品信息交给模型组织回答。

“100 美元以内”这类硬条件不能只靠向量相似度保证，需要字段过滤或权威数据复核。工具可以设计成 `searchProducts(query, filters)` 和 `getProductDetails(itemIds)`，agent 根据对话选择调用，而不是让模型随意查询数据库。推荐理由必须依据实际属性，没有合适结果就说明找不到，不能编造商品或承诺库存。

### AI 服务故障和安全怎么处理

Embedding 服务或 Milvus 不可用时，可以降级到普通商品搜索；聊天模型不可用时，可以显示检索到的商品卡片。商品介绍属于不可信内容，即使里面出现“忽略指令”也不能让 agent 执行。检索工具只开放必要的读取权限，不应该因为用户聊天就自动下单或付款。

效果可以通过固定查询集的相关性、可售商品比例、响应延迟、每次对话成本和实际点击行为来验证。没有评测结果前，不写“推荐准确率提升 40%”或“转化率提升 20%”。检索质量提升和交易增长是不同的结论，后者通常还需要受控业务实验。

### 面试时可以这样说

“I built a shopping assistant that lets users describe what they want in natural language. The service converts product descriptions into embeddings, stores them in Milvus, and retrieves relevant products during the conversation. The assistant checks current product details before explaining its suggestions.”

## 11. 其余条目也要能讲清楚

订单状态机不是一个特别复杂的框架，而是一套明确的允许转换规则。订单、支付和库存预留分别记录状态，避免用一个状态字段表达所有事情；例如订单取消但退款处理中是合理组合。检查必须落在条件数据库更新中，不能仅在 Java 里先读状态再决定修改，因为并发请求可能同时读到旧状态。

CompletableFuture 和线程池这一条强调并行读取互相独立的商品资料，不是把每个库存扣减无条件并行化。线程池限制执行数量，队列限制等待任务；远程调用还需要超时。更多线程不会自动带来更高吞吐，数据库和 HTTP 连接池也有上限。

JUnit 5 用来组织测试，Mockito 用来模拟依赖，Testcontainers 用真实数据库或消息系统容器验证集成行为。模拟 repository 永远返回成功，无法证明库存不超卖。并发测试需要真正同时发请求并检查最终库存；重复消息测试需要检查业务效果只发生一次，而不只是收到一个成功响应。

Docker 把服务及运行依赖打包，Kubernetes 管理实例、网络接入和发布。滚动更新让新旧版本短暂并存，所以接口、事件格式和数据库迁移需要向后兼容。它不保证绝对零停机，也不自动修复坏的 schema 变更。

## 12. 英文简历条目 按业务范围和成果组织

以下保留技术关键词，但每条只强调一个主要业务目标。它们按完整目标版本撰写，AI 功能依据你提供的描述组织；本次没有验证该功能的代码或部署。正式使用时保留与你真实经历相符的条目，不需要把学习文档中的全部机制都塞进简历。

### Description

Developed backend services for an e-commerce platform supporting customer shopping and seller operations, including account management, product listings, inventory, checkout, payments, and order tracking. Built Java and Spring Boot microservices with REST APIs and Kafka-based communication, focusing on transaction reliability, concurrent order processing, and efficient product access.

Extended product discovery through Elasticsearch keyword search and an AI shopping assistant backed by Milvus, helping customers find and compare products through search and conversation. Supported cloud deployment on AWS with Docker and Kubernetes, along with application monitoring and automated testing for critical shopping workflows.

### Responsibility

- Developed backend microservices using Java 17 and Spring Boot to support customer accounts, seller product management, and checkout operations. Built RESTful APIs and integrated service communication through OpenFeign, Eureka, and Spring Cloud Gateway.
- Built order management workflows covering order creation, payment, shipping, cancellation, and refunds. Enforced order status rules and coordinated updates across services to keep customers and sellers informed throughout the purchase lifecycle.
- Implemented inventory management for stock updates, reservations, and releases using PostgreSQL and Spring Data JPA. Handled competing checkout requests to prevent overselling and repeated stock deductions during retries.
- Developed payment and refund workflows with protection against duplicate charges and support for interrupted payment requests. Integrated payment APIs using WebClient and Resilience4j to handle provider timeouts and temporary failures without treating uncertain payments as completed transactions.
- Built Kafka-based event processing for order, payment, and inventory updates, allowing downstream services to process changes independently. Added duplicate-event handling and recovery for failed messages to support reliable processing during service interruptions.
- Developed data models using Hibernate and PostgreSQL for customer, order, and inventory records, and maintained Cassandra product views for catalog reads. Separated transactional updates from product browsing while keeping catalog changes synchronized across data stores.
- Implemented Redis caching for frequently accessed product details, seller listings, and order information to reduce repeated database reads. Managed cache expiration and updates following product changes, and addressed cache failures affecting product retrieval.
- Implemented product keyword search using Elasticsearch, supporting searches across product names and descriptions with category and price filters. Connected search results with current product information to help customers browse and narrow their selections.
- Improved checkout request handling by fetching independent product details concurrently with CompletableFuture and a bounded thread pool. Controlled parallel calls to downstream services while keeping inventory updates and order persistence within their appropriate transaction boundaries.
- Implemented authentication and access control using Spring Security and JWT, including session renewal and buyer and seller permissions. Restricted access to customer-owned orders and seller-owned products across account, shopping, and management APIs.
- Deployed the application and supporting infrastructure on AWS, managing cloud networking, access permissions, and persistent storage. Used Docker and Kubernetes to run services with health checks, resource limits, and rolling updates for application releases.
- Established application monitoring using Spring Boot Actuator, Micrometer, Prometheus, and Grafana to track request latency, errors, payment outcomes, and JVM health. Added structured logs and trace IDs to investigate failed requests across service boundaries.
- Developed unit and integration tests using JUnit 5, Mockito, and Testcontainers for checkout, inventory, and payment workflows. Covered concurrent purchases, duplicate requests, repeated Kafka events, and service failures to validate business behavior beyond successful transactions.
- Developed an AI-powered shopping assistant that lets customers describe their needs, ask follow-up questions, and receive product suggestions. Processed product descriptions into text chunks and embeddings stored in Milvus, and connected an agent to product search tools to retrieve relevant items and explain recommendations.

### 数字如何补充

这版用业务范围和处理场景增加信息量，不用未验证的百分比填充篇幅。Java 17 是技术版本，不是业绩指标；百万日活、峰值 RPS、延迟降幅、测试覆盖率和发布耗时都需要对应证据。拿到数据后，可以用下面这种形式替换相关条目的部分内容，方括号不能原样提交到简历。

- 性能：Reduced checkout p95 latency from [measured baseline] to [measured result] under [request rate and test conditions] by fetching independent product details concurrently.
- 缓存：Reduced catalog database reads from [baseline reads/s] to [result reads/s] at [API request rate], with a measured cache hit rate of [percentage].
- 测试：Verified stock limits with [number] concurrent purchase requests and validated duplicate-payment handling across [number] repeated requests.
- AI：Evaluated product search on [number] representative queries, measuring [defined relevance metric] and end-to-end response time.

## 13. 如何使用这份说明准备面试

新增范围记录：完整目标版本增加 Elasticsearch 商品关键词检索，以及整个平台的 AWS 部署。上面的英文条目属于目标版本简历模板，本次只记录需求，没有新增搜索代码或执行云部署。AWS 的具体服务选型、部署区域、资源规格和预算尚未确定，不把 EKS、RDS 等选择写成已作出的决定。

Elasticsearch 这条强调关键词和字段筛选，Milvus 这条强调语义检索与对话推荐。AWS 和 Kubernetes 已合并成一条完整部署经历，支付去重与第三方支付调用也合并为一条支付业务经历，避免同一功能重复占用简历篇幅。Outbox、请求指纹等细节保留在前面的学习说明中，不再塞进简历条目。

先用自己的话解释业务问题，再说你保存了什么数据、在哪里检查以及失败后怎么办。例如不要只说“实现幂等”，而是说“用户重复点击付款，我让这几次请求找到同一笔支付记录，不再创建新的扣款”。再补数据库唯一约束和第三方支付编号，面试官会更容易理解你实际做了什么。

不理解的机制先不要放进简历，只会背名称反而增加追问风险。术语不是不能用，而是应该有具体例子支撑。能把“同一订单怎么避免扣两次款”解释清楚，比一句话同时列出十个分布式系统名词更有说服力。

## 14. 最新版简历的 14 条完整讲解

这里按你最新粘贴的简历逐条解释，不只挑某个短语。AWS 与 Kubernetes 分为两条，监控不额外计入这 14 条；前面的监控说明仍保留。以下业务流程采用完整目标版本，解释不代表本次验证了对应功能已完成，所有计算例子也不代表实测成绩。

### Bullet 1 Java 微服务 REST API 与服务间通信

> Developed backend microservices using Java 17 and Spring Boot to support customer accounts, seller product management, and checkout operations. Built RESTful APIs and integrated service communication through OpenFeign, Eureka, and Spring Cloud Gateway.

#### 这整条说明你负责什么

你负责的不是单独写几个 Controller，而是把购物业务拆成边界清楚的服务，并让前端和各服务可以正确协作。Account 管用户资料，Item 管商品和库存，Order 管下单记录与流程，Auth 管登录身份。拆分的价值是这些业务可以分别修改和发布，但也带来网络故障和跨服务一致性问题，不应该说微服务天然比单体快。

Java 17 是语言和运行平台，Spring Boot 帮助组织依赖、配置、Web 接口和运行生命周期。REST API 是服务对外表达操作的方式，例如查询商品、创建订单。OpenFeign 把服务间 HTTP 调用表达成 Java 接口；它并没有把远程调用变成本地调用，依然有连接失败、超时和响应解析问题。

#### 一次下单如何经过这些组件

浏览器提交订单到 Gateway，Gateway 根据路径把请求路由给 Order Service。Order Service 通过 Feign 调 Item Service 获取商品资料或预留库存，再处理自己的订单数据。服务使用 Eureka 时，实例注册服务名称与地址，调用方结合服务发现和负载均衡选择实例，不需要把每个实例的 IP 写进业务代码。

Gateway 是外部 API 入口，Eureka 是服务地址目录，Feign 是发起 HTTP 请求的客户端，它们不能互相替代。内部调用通常不必绕外部 Gateway 一圈。若以后采用 Kubernetes Service DNS，可以重新评估 Eureka 是否仍有必要；不要为了技术数量同时保留两套没有明确分工的发现机制。

#### 异常处理和追问

输入参数在入口校验，业务错误与基础设施错误分别返回稳定的响应。Feign 必须配置超时，重试写操作前必须知道是否幂等。服务发现列表里有地址不代表实例此刻一定能处理请求，因此还需要连接错误处理和健康管理。

面试官问“为什么拆服务”，可以回答业务职责、独立发布和数据所有权，而不是只说方便扩容。问“为什么用 Java 17”，可以讲团队统一运行版本、依赖兼容性和维护成本；不要把语言版本本身当作性能提升证据。

#### 英文回答

“I built backend services for accounts, products, and checkout. The frontend enters through Spring Cloud Gateway, while services call each other through Feign clients. Eureka provides service locations, so the application does not depend on fixed instance addresses. I also handle timeouts and API errors because a remote call can fail even when the Java interface looks like a local method.”

### Bullet 2 订单管理与状态变化

> Built order management workflows covering order creation, payment, shipping, cancellation, and refunds. Enforced order status rules and coordinated updates across services to keep customers and sellers informed throughout the purchase lifecycle.

#### 这整条说明你负责什么

这条讲的是从买家提交订单到最终履约或取消，系统如何持续跟踪业务进度。它包括创建订单、记录金额和商品快照、接收支付结果、记录发货、处理取消和退款，而不只是创建一行订单记录。Shipping 在这个范围中可以是记录卖家发货和物流单号，不等于已经集成了完整承运商平台。

订单、支付和库存状态分别回答三个问题：订单走到哪里、钱处理到哪里、货有没有被占用。比如订单已取消但退款处理中是合理组合，不能因为订单取消就把支付标成已退款。前端从订单接口读取这些结果，才能给用户显示准确进度；是否推送通知是另一个接口或事件消费能力。

#### 正常流程和数据库规则

用户创建订单后，系统保存商品单价和数量快照，避免以后商品涨价改变历史订单。库存预留成功才继续相关支付步骤，支付明确成功后订单才能进入已支付状态。发货需要满足付款和库存条件，完成订单则依据明确的确认收货或业务规则。

状态转换必须在数据库中检查原状态或版本。例如支付成功处理只允许待支付订单按规则变为已支付，不能把已取消订单无条件覆盖。两个请求并发修改时，只有满足数据库条件的更新成功，另一个请求重新读取结果再判断。

#### 取消与支付竞争怎么办

如果买家取消时支付结果还未知，不能直接认定“没有扣款”。后续确认实际扣款成功时，必须记录资金事实，再按取消政策发起退款。退款失败就保持待处理并重试，不能为了让订单看起来结束而伪造退款成功。

这项功能的价值可以用允许的转换、拒绝的转换和恢复场景覆盖率来验证，而不是硬写一个 QPS 数字。面试官追问时，要能举出“取消与付款同时到达”的例子，并解释哪一个服务负责推进订单、哪里保存进度。

#### 英文回答

“I manage the order from creation through payment, shipping, and completion, including cancellation and refunds. Each operation checks the current state before making a change. Payment and inventory have their own states, so a cancelled order can still show that its refund is pending. This also lets the workflow recover correctly when payment and cancellation happen at the same time.”

### Bullet 3 PostgreSQL 库存管理与并发购买

> Implemented inventory management for stock updates, reservations, and releases using PostgreSQL and Spring Data JPA. Handled competing checkout requests to prevent overselling and repeated stock deductions during retries.

#### 这整条说明你负责什么

库存管理包含卖家补货、买家下单占用库存、取消后释放库存，以及付款后确认占用。这里要区分“货还在仓库里”和“还有多少可以卖”：已预留的货可能还没有发出，但不能再次卖给别人。需要明确库存字段含义，不能把各种数量都叫 stock 后随意加减。

Spring Data JPA 提供库存实体和数据库访问接口，PostgreSQL 才真正执行事务、唯一约束和并发写入。`synchronized` 只能约束一个进程内的线程，多个服务实例时不能作为防超卖的最终保障。

#### 一次库存预留如何完成

请求包含订单、商品、数量以及稳定的预留身份。Item Service 在同一个本地事务里建立预留记录并执行带数量条件的原子更新；库存不足时整个事务回滚。重复请求发现同一预留记录后返回已有结果，而不是再减库存；数量不一致则拒绝冲突。

释放也不是简单执行“库存加二”。服务先检查预留仍然是 RESERVED，成功改为 RELEASED 的那次事务才恢复数量；重复释放不再增加。补货也要与并发预留协调，使用明确的增量操作或版本校验，避免卖家保存一个旧的绝对库存值覆盖刚发生的扣减。

#### 如何证明有效

可以从库存 10 开始，让 100 个并发请求各预留一件，检查成功数量最多为 10、库存始终不为负。再让同一请求重复 20 次，检查只发生一次对应扣减，随后重复释放验证只恢复一次。这些是示例验收条件，测试应连接真实 PostgreSQL，而不是只 mock repository。

面试官还可能问一个订单多件商品如何处理。如果是 Item Service 内的批量预留，可以在本地事务中全部成功或全部回滚，并按稳定顺序访问库存行降低死锁概率；如果逐个远程调用，则可能部分成功，需要单独的恢复流程。

#### 英文回答

“I manage available stock and track which quantities are reserved for each order. PostgreSQL checks the available quantity and deducts it in one operation, so concurrent buyers cannot spend the same last unit. I also track reservation status so retries do not deduct stock twice and repeated cancellation does not add stock back twice.”

### Bullet 4 支付 退款与第三方故障

> Developed payment and refund workflows with protection against duplicate charges and support for interrupted payment requests. Integrated payment APIs using WebClient and Resilience4j to handle provider timeouts and temporary failures without treating uncertain payments as completed transactions.

#### 这整条说明你负责什么

这条的中心是钱的处理结果必须准确。用户重复点击付款、浏览器断网、支付服务重启和提供方超时，都不应该让同一次购买多扣钱。退款也要有自己的记录和唯一操作身份，不能因为重试把同一笔款退两次。

WebClient 负责发 HTTP 请求，Resilience4j 帮助限制等待时间、重试次数和并发调用，并在下游连续故障时暂时熔断。它们是故障控制工具，不替代支付业务记录，也不能保证第三方操作被客户端超时取消。

#### 支付流程如何串起来

服务根据可信订单金额建立一条支付尝试，保存固定支付编号，然后在数据库事务之外调用提供方。明确成功后，在新的短事务中保存支付结果并通知订单流程。重复请求应找到原来的支付尝试，重复回调也只能让同一次状态转换生效一次。

超时时先保留处理中状态，再用原编号查询或接收经验证的回调。提供方明确失败后才根据规则允许新尝试；只要结果未知，就不能换编号盲目扣款。第三方退款同样在确认结果后标为已退款，不能把“退款请求已发送”当作“钱已到账”。

#### 安全与容错边界

回调需要验证来源、签名及关联的金额和订单，不能收到任意 success 字段就信任。对永久拒付或参数错误不应自动重试；对临时错误的重试必须有幂等保证和整体时间预算。熔断期间，如果没有可靠受理请求就返回服务不可用，已经受理但结果未知则返回可查询的处理状态。

如果没有接入真实支付商，面试练习应称为模拟提供方集成，不能把 stub 测试说成真实资金交易。验证重点是外部实际效果：模拟响应丢失并重试后只发生一次扣款，而不只是数据库里只有一条记录。

#### 英文回答

“I track each payment and refund as a separate operation with a stable reference. Retries return or check the existing operation instead of starting another charge. WebClient calls the provider, and Resilience4j limits waiting and repeated calls. When a response is lost, the payment stays pending until a query or a verified callback confirms the outcome.”

### Bullet 5 Kafka 事件与失败恢复

> Built Kafka-based event processing for order, payment, and inventory updates, allowing downstream services to process changes independently. Added duplicate-event handling and recovery for failed messages to support reliable processing during service interruptions.

#### 这整条说明你负责什么

付款成功后，订单要更新，通知服务可能要发消息，统计系统也可能需要记录。让支付接口同步等待所有下游，会把下游延迟和故障叠加到付款响应上。Kafka 让已确认的业务变化先被可靠记录，再由不同消费者独立处理，代价是下游看到变化可能有短暂延迟。

不同 consumer group 可以各自完整消费同一事件流；同一 group 内的消费者分担分区。支付和订单等服务需要明确谁发布事实、谁推进流程，避免每个消费者都随意修改同一业务状态。

#### 从数据库变化到消费完成

生产服务把业务变化和待发事件一起提交，publisher 之后发送到 Kafka，避免数据库成功而消息丢失。事件包含 eventId、业务 ID、类型、版本和必要数据。消费者先判断是否已经处理过，再在自己的事务中保存处理标记和业务变化，成功后推进消费进度。

事件如果调用外部系统，还需要外部操作幂等，不能只靠本地处理标记。临时错误按策略重试，多次失败的事件保存到失败 topic 并告警；修复后选择相关事件限速重放，保留原 ID，让去重机制继续工作。

#### 顺序和性能怎么解释

同一 topic 中按 orderId 分区，可以把同一订单的事件放在一起，但不同 topic 没有全局顺序，重试 topic 也可能改变处理先后。消费者因此要检查状态和版本，不能盲目把晚到的旧消息覆盖新结果。增加消费者只在分区和下游容量允许时有帮助，不是实例越多越快。

观察价值时可以看 API 是否移除了不必要的同步等待，以及 Kafka lag、最老事件年龄和恢复速度。比如积压 10,000 条、持续进入 100 条/秒、消费能力 200 条/秒，净消化速度是 100 条/秒，理想恢复约需 100 秒；这是容量计算，不是实测。

#### 英文回答

“I publish order, payment, and inventory changes so downstream services can handle them independently. Each consumer tracks processed events and applies its own database update safely. Temporary failures are retried, and unresolved messages are kept for investigation and replay. I also check business state because message delivery alone does not guarantee the order workflow is correct.”

### Bullet 6 Hibernate PostgreSQL 与 Cassandra 数据职责

> Developed data models using Hibernate and PostgreSQL for customer, order, and inventory records, and maintained Cassandra product views for catalog reads. Separated transactional updates from product browsing while keeping catalog changes synchronized across data stores.

#### 这整条说明你负责什么

这一条是数据建模，不是简单说会连接两个数据库。用户资料、订单金额、订单明细和可售库存需要清晰的关系与事务约束，所以放在 PostgreSQL。Cassandra 保存面向查询的商品副本，用来支撑目录读取，不负责裁决钱有没有扣、库存能不能卖。

Hibernate 负责 Java entity 与关系表之间的映射，Spring Data JPA 在它上面提供 repository 访问方式。它们可以减少重复持久化代码，但自动生成的 SQL 仍需检查，尤其是订单与明细关系导致的额外查询、过度加载和分页问题。

#### 表如何配合业务

Order 保存 userId、状态、币种、总额和创建时间，OrderItem 保存购买时的名称、单价和数量快照。这样商品改名或改价后，历史订单仍然反映当时交易内容。金额采用明确精度的 decimal/numeric 与 BigDecimal，不能使用二进制浮点数随意累加钱。

Cassandra 按查询方式建立投影，例如按 itemId 查详情，以及按 sellerId 和受控 bucket 查卖家商品。后者的分区大小要考虑大卖家，分桶也会增加跨桶分页复杂度，不能只写一个 sellerId 主键就宣称无限扩展。查询表是否值得增加，需要实际访问模式支撑。

#### 两边怎么同步和恢复

商品修改先提交 PostgreSQL，再由事件更新 Cassandra。投影记录商品版本，避免旧更新覆盖新更新；删除也需要版本或删除标记等策略，防止晚到事件把下架商品恢复。整个链路是最终一致，不是两个数据库同一个事务。

Cassandra 不可用时，权威数据仍在 PostgreSQL，但读请求是否回源必须有容量限制。投影可以根据权威数据重建；重建期间要协调快照和增量事件，避免漏掉扫描期间发生的更新。面试时应能解释为什么这个项目选择两个存储，也应承认较小负载下单 PostgreSQL 会更简单。

#### 英文回答

“I use PostgreSQL for authoritative customer, order, and inventory data. Hibernate maps those records to Java entities, and order lines keep the price and product information from the time of purchase. Cassandra stores product views organized for catalog queries. Product changes reach those views through events, so I handle versioning and recovery rather than assuming both databases update at the same instant.”

### Bullet 7 Redis 缓存与实际故障排查

> Implemented Redis caching for frequently accessed product details, seller listings, and order information to reduce repeated database reads. Managed cache expiration and updates following product changes, and addressed cache failures affecting product retrieval.

#### 这整条说明你负责什么

这条包含缓存什么、什么时候更新、缓存坏了怎么办三个部分。商品详情和卖家列表常被重复读取，适合缓存；订单状态虽然可以缓存，但用户付款后需要及时看见更新。不同对象的一致性要求不一样，不能所有 key 都用一个 TTL 然后认为问题解决。

缓存键应包含能区分结果的参数，例如商品 ID、卖家 ID，以及列表页码和过滤条件。订单缓存命中也不能绕过 ownership 检查：知道 orderId 不代表有权读取它。缓存的是查询结果，不是用户权限的替代品。

#### 读取和更新如何工作

读取先查 Redis，命中则返回，未命中再查数据源并填充缓存。商品修改提交后使相关详情和列表失效，消费者完成 Cassandra 更新后也要考虑失效，避免缓存从旧投影重新填入旧值。TTL 给遗漏的失效操作提供时间上限，但不保证严格实时一致。

热门商品过期时可以合并同时发生的加载，不存在商品可以短暂缓存 not-found，批量 key 的过期时间可以适当错开。遇到 Redis 超时，回源也要限制并发，不能让缓存故障把数据库一并打垮。对可以接受短暂旧数据的商品描述可以定义降级策略，库存扣减则回到权威事务路径。

#### 这条里的真实调试经历

此前后端写入的商品列表与 Redis 反序列化所需类型信息不匹配，导致再次读取失败，并被错误分派路径掩盖为 403。修复使用的列表实现与缓存版本前缀后，添加序列化往返测试，连续商品读取返回 200 和三件商品。这个例子说明缓存有 key 不等于应用可以正确读取 value，不能只看 Redis 是否启动。

价值用命中率、数据源读次数、缓存错误率和接口延迟一起判断。命中率 85% 可以解释一部分回源减少，但不能直接推导接口快了 85%。冷缓存、热缓存和 Redis 故障场景要分开测。

#### 英文回答

“I cache frequently requested products and listings so repeated reads do not always reach the database. Product changes invalidate related entries, and expiration limits how long old data can remain. I also investigated a serialization issue where a cached product list could not be read back, and added a regression test using the actual Redis serializer.”

### Bullet 8 Elasticsearch 商品关键词检索

> Implemented product keyword search using Elasticsearch, supporting searches across product names and descriptions with category and price filters. Connected search results with current product information to help customers browse and narrow their selections.

#### 这整条说明你负责什么

这条负责传统搜索框：用户输入关键词，系统找到相关商品，再按类别和预算缩小结果。它与 Milvus 的语义推荐有交集，但侧重点不同：关键词搜索需要可解释的文本匹配、过滤和排序，而 AI 助手还要理解自然语言需求及后续对话。

Elasticsearch 保存可重建的商品索引，不接管商品权威写入。商品名称和描述使用适合全文搜索的字段，类别、商品 ID 等使用适合精确匹配的字段，价格使用数值字段。分词把文本拆成可搜索的词项，字段权重可以让名称匹配比描述里偶然出现更重要。[Elastic 字段说明](https://www.elastic.co/guide/en/elasticsearch/reference/current/text.html)

#### 一次搜索如何完成

用户输入“waterproof backpack”，搜索服务组合文本匹配与类别、价格条件，返回分页结果和 itemId。再根据接口设计取得或校验当前展示资料，排除已下架商品；结账时仍必须重新验证最终价格和库存。不要为每个结果各调用一次商品接口，必要时使用批量读取避免列表变成大量 HTTP 请求。

商品更新经事件进入搜索索引，使用版本或等效策略避免旧事件覆盖新数据。写入索引与搜索可见之间还存在 refresh 延迟，所以“更新成功”不一定表示下一次搜索立即看见。[Elastic 近实时检索说明](https://www.elastic.co/docs/manage-data/data-store/near-real-time-search)

#### 故障与验证

搜索服务故障时可以展示受限的分类浏览或明确提示搜索暂时不可用，不应无上限地退化成数据库全表模糊扫描。索引损坏可以重建，但应使用新索引构建、校验并切换的流程，同时处理持续变化的数据。无限扩大返回条数和很深的 offset 分页也会带来资源压力。

验证既看响应时间，也看搜索相关性：找一组代表性查询，检查目标商品是否出现在前几名、过滤条件是否正确、下架商品是否被排除。语义相近不等于关键词一定匹配，搜索框的容错、同义词和语言需求需要单独定义。

#### 英文回答

“I use Elasticsearch for product-name and description searches, with category and price filters. Product changes update the search index asynchronously. Search results lead back to current catalog data, and checkout validates the final price and stock separately. I evaluate both response time and whether the returned products match common customer queries.”

### Bullet 9 CompletableFuture 与有界线程池

> Improved checkout request handling by fetching independent product details concurrently with CompletableFuture and a bounded thread pool. Controlled parallel calls to downstream services while keeping inventory updates and order persistence within their appropriate transaction boundaries.

#### 这整条说明你负责什么

一个订单有多件商品，读取 A 的详情通常不依赖读取 B 的结果，可以同时查询。CompletableFuture 表示这些还没完成的查询及其后续组合，线程池则决定同时运行多少个阻塞调用。不是把整个下单方法丢到后台就算优化，也不是商品越多就无限开线程。

线程数限制正在执行的工作，队列容量限制等待的工作；满了以后要有明确拒绝策略。Feign 等阻塞 HTTP 调用在等待期间仍占用工作线程，所以线程池、HTTP 连接池和下游容量需要一起看。Gateway 的 event-loop 线程不能被这种阻塞等待随意占住。

#### 并行读和事务写如何分开

Order Service 提交几个独立读取任务，等待结果并校验，再进行库存预留和订单持久化。若有关键商品读取失败，应按规则终止本次结账准备，而不是静默漏掉那一行。库存写入的部分成功问题与读并行不同，不能直接对扣库存循环套一个 parallelStream。

数据库事务不自动从请求线程传播到工作线程。需要事务的持久化步骤由受 Spring 管理的服务边界开启短事务，不要在等 HTTP 的整个期间持有数据库连接。对远程调用还要有客户端超时；future 超时或取消本身不能保证已经运行的底层 HTTP 请求停止。

#### 数字怎么讲才准确

假设四次独立读取各约 80ms，串行的等待部分大约 320ms，有足够资源时并行接近最慢的一次加协调开销，但订单总耗时还有库存和数据库操作。它把等待重叠起来，并没有把四次网络请求减少为一次。批量接口减少请求数是另一种优化，不能混写成绩。

面试时需要说明拒绝行为、队列等待和下游超时，不只背 corePoolSize、maxPoolSize。线程池塞满了还无限排队，会把快速失败变成长期超时，反而伤害用户体验。

#### 英文回答

“I fetch independent product details in parallel before creating the order. CompletableFuture combines the results, and a dedicated bounded pool limits how many calls can run. This overlaps network waiting without opening unlimited threads. Inventory changes and order persistence remain separate steps with their own transaction and failure handling.”

### Bullet 10 Spring Security JWT 与资源归属

> Implemented authentication and access control using Spring Security and JWT, including session renewal and buyer and seller permissions. Restricted access to customer-owned orders and seller-owned products across account, shopping, and management APIs.

#### 这整条说明你负责什么

这条同时覆盖身份认证、权限判断和登录会话生命周期。身份认证回答用户是谁，角色判断回答买家和卖家各能做什么，归属校验回答这个用户能不能修改这一件商品或查看这一笔订单。只要 token 合法就允许访问所有订单，仍然是严重的越权问题。

Auth 负责登录凭证和发 token，Account 管用户资料，两边使用一致的身份 ID。Gateway 可以完成入口校验，业务服务根据可信身份做更具体的权限判断。内部服务也不能仅因为请求带了一个 userId header 就无条件信任它，需要有明确的内部认证边界。

#### 请求和会话更新如何完成

用户登录后取得 access token，访问接口时服务验证签名、有效期、issuer、audience 等条件，再把用户和角色放进安全上下文。商品更新接口先要求 SELLER，再查询商品所有者是否匹配；订单查询也要限制用户身份。请求体里的 sellerId 或 userId 不应该覆盖已验证身份。

Access token 过期后通过 refresh token 更新会话，服务检查刷新凭证是否到期或撤销。采用轮换时消费旧凭证、生成新凭证并保存关系，退出登录撤销会话。短期 access token 在没有额外撤销检查时可能仍然有效到过期，这一点不能省略。

#### 异常与测试

无有效身份通常返回 401，有身份但无权限通常返回 403；访问他人资源可以按隐私策略返回 404。日志不能记录完整 token 或密码。角色变化后旧 JWT 可能暂时保留旧角色，因此权限敏感操作需要结合令牌寿命或在线检查策略。

测试应覆盖过期 token、伪造 token、buyer 修改商品、seller 修改他人商品和用户读取他人订单。不是只验证登录成功能拿到一个字符串。Spring Security 的默认配置也不会自动帮你完成所有这些业务归属规则。

#### 英文回答

“I use Spring Security to establish the caller's identity and enforce API permissions. JWT carries the user identity and roles, but services still check who owns each order or product. I also manage session renewal and revocation, and test unauthorized requests instead of testing only successful login.”

### Bullet 11 AWS 云环境部署

> Deployed the application and supporting infrastructure on AWS, managing cloud networking, access permissions, and persistent storage.

#### 这整条说明你负责什么

这条讲应用在云上需要什么运行环境，不是列举听过的 AWS 服务。你要能说明流量从哪里进来，应用运行在哪，数据库怎样访问，凭证由谁管理，实例替换后数据是否还在。当前具体 AWS 选型还没有确定，下面给的是面试学习用的可选映射，不是已经执行的配置。

如果使用 AWS 托管 Kubernetes，可以选择 EKS；镜像可以放 ECR，PostgreSQL 可以选择 RDS，商品图片可以存 S3。每一个选择都要有实际采用依据，不需要把整个 AWS 产品列表放到简历。Elasticsearch 与 Amazon OpenSearch 也不能直接当作同一个产品名称，采用后者需要重新核实兼容性和描述。

#### 网络和权限怎样解释

VPC 定义云上的网络范围，子网划分不同网络位置。面向用户的负载均衡入口接受 HTTPS，应用和数据库按需求放在非公网直接访问的网络中，通过受限规则通信。数据库不需要为了让应用访问而向整个互联网开放端口；应用访问外部支付接口还需要正确的出站路径。

IAM 管理身份可以调用哪些 AWS 操作，Security Group 管理网络上允许哪些连接，两者职责不同。应用使用工作负载身份或合适的角色取得必要权限，不把长期管理员密钥打进 Docker 镜像。密钥、数据库密码的保存与轮换是独立的运维责任。

#### 数据持久化和故障恢复

容器的临时文件系统不适合保存唯一一份订单数据。数据库和需要持久化的中间件必须有明确的存储、备份和恢复方案；Cassandra、Kafka、Milvus 等都不能只因为跑在云上就被当作自动高可用。备份成功还不够，需要恢复演练，确认恢复时间和可接受的数据损失窗口。

AWS 管理部分基础设施，不意味着应用安全、数据模型、权限和备份验证都由云厂商负责。[AWS 共享责任说明](https://aws.amazon.com/compliance/shared-responsibility-model/)解释了这个边界。面试中应具体说自己配置或维护了哪部分，不应把平台团队的工作全部算到个人名下。

#### 英文回答

“This responsibility covers the cloud environment around the application: network access, application permissions, and durable storage. The public entry point accepts customer traffic, while backend services and databases communicate through restricted network paths. Credentials are managed separately from application images, and persistent data needs backup and recovery procedures rather than relying on container storage.”

### Bullet 12 Docker Kubernetes 与发布管理

> Used Docker and Kubernetes to run services with health checks, resource limits, and rolling updates for application releases.

#### 这整条说明你负责什么

Docker 把应用及其运行环境打包成镜像，使不同环境使用同一个构建产物。Kubernetes 管理容器实例在哪里运行、保持多少副本、怎样接收流量和怎样替换版本。它与前一条 AWS 的关系是应用运行管理与云资源环境的分工，Kubernetes 也可以运行在其他环境。

Deployment 描述应用副本和更新策略，Pod 是运行容器的单位，Service 提供稳定的服务访问入口。镜像使用明确版本或摘要，配置从环境或配置对象注入，而不是为每个环境修改代码再随意打包。

#### 健康检查与资源配置

Startup probe 给服务启动时间，readiness 表示能否接收正常请求，liveness 用于判断是否需要重启容器。Readiness 失败本身不是重启指令，liveness 也不应该因为共享数据库短暂故障就让所有实例反复重启。

Resource requests 参与调度和容量安排，limits 约束容器使用资源。CPU 达到限制可能被节流，内存超过限制可能导致 OOM 终止，所以 Java 堆大小需要给线程栈、直接内存和其他开销留空间。配置一个 1GB 内存限制再把堆也设成 1GB 不是完整的内存规划。

#### 滚动更新怎样避免业务中断

发布时逐步启动新实例，通过 readiness 后再替换旧实例，旧实例停止接收新请求并尽量完成已有工作。Kafka 消费者和后台任务也需要处理终止信号，未完成工作应由持久化进度恢复，而不是直接提交所有 offset。实例能正常滚动不代表业务 schema 也兼容，新旧版本共存期间 API 和数据库都要能同时支持。

发现问题时可以回滚镜像，但不能自动撤销真实扣款或已执行的破坏性数据库迁移。验证应检查订单成功率、错误率和延迟，而不只是所有 Pod 都是绿色。自动扩容也依赖指标与资源供应，不能消除数据库瓶颈或承诺零停机。

#### 英文回答

“I package each service as a Docker image and run it through Kubernetes. Health checks control when an instance can receive traffic and whether it needs restarting. Resource settings prevent one container from using unlimited capacity. During releases, new instances become ready before old ones are removed, while graceful shutdown and compatible database changes protect in-flight work.”

### Bullet 13 JUnit Mockito 与 Testcontainers 测试

> Developed unit and integration tests using JUnit 5, Mockito, and Testcontainers for checkout, inventory, and payment workflows. Covered concurrent purchases, duplicate requests, repeated Kafka events, and service failures to validate business behavior beyond successful transactions.

#### 这整条说明你负责什么

这条不仅说用了三个测试工具，还说明测试目标是业务在竞争、重复和失败时仍然正确。JUnit 5 负责测试组织和断言，Mockito 在单元测试里替代外部依赖，Testcontainers 启动真实基础设施进行集成验证。三者是互补关系，不是任选一个就覆盖全部风险。

单元测试适合非法状态转换、金额计算、角色条件和异常分支。数据库唯一约束、事务回滚、行锁与序列化兼容则应该连接真实组件检查。使用 Mockito 验证 reserve 被调用一次，并不能证明多个数据库事务不会超卖。

#### 怎样组织完整测试流程

库存集成测试准备固定库存，用并发屏障让请求尽量同时开始，等待所有结果后检查成功数、剩余数量和预留记录。支付测试让模拟提供方先完成扣款再丢失响应，然后重试，检查提供方记录的实际效果只有一次。这个模拟系统也需要有可观察的幂等行为，不是简单返回成功字符串。

Kafka 测试重复投递同一 eventId，检查业务结果只更新一次，再模拟消费者提交数据库之后、确认 offset 之前中断。Outbox 测试暂停发送路径，验证业务提交后仍保留待发事件，并在恢复后最终送达。等待异步结果使用有截止时间的条件轮询，而不是固定睡几秒碰运气。

#### 怎么解释覆盖和边界

测试报告要区分代码覆盖率和业务保障。100% 行覆盖不代表覆盖支付取消竞争，也不代表跨区域故障可恢复。单机容器集成测试不证明生产多副本容量，所以吞吐、p95/p99 和大规模故障需要另做具有代表性的测试。

这个 bullet 可以用已完成的测试数量或明确并发条件补充，但不能把目标场景写成已跑通的成绩。测试失败时应输出订单 ID、最后状态和相关日志，使问题能复现，而不是只写一个超时断言。

#### 英文回答

“I use unit tests for business decisions and integration tests for real database and messaging behavior. For example, concurrent checkout tests verify that stock never becomes negative, and duplicate-payment tests check the provider effect rather than only the API response. I also test interruptions between commits and acknowledgements because those are common points for duplicate processing.”

### Bullet 14 AI 购物助手 Milvus 与商品推荐

> Developed an AI-powered shopping assistant that lets customers describe their needs, ask follow-up questions, and receive product suggestions. Processed product descriptions into text chunks and embeddings stored in Milvus, and connected an agent to product search tools to retrieve relevant items and explain recommendations.

#### 这整条说明你负责什么

这条是一项用户功能和两条技术流程：用户可以连续聊天找商品，后台需要商品内容入库，以及聊天时搜索并生成回答。与普通搜索框不同，用户可以说“适合周末徒步、轻一点、预算 100 美元”，接着补充“希望防水”，系统要继承有效需求并更新搜索条件。

这不自动意味着已经训练了个性化推荐模型。当前描述更准确地属于基于商品内容的语义检索和对话推荐，依据的是商品介绍与用户当下需求，不是对百万用户行为训练出的协同过滤模型。

#### 商品入库流程

读取商品标题、描述和必要属性，清理无意义内容；较长文本按可理解的段落分块，短描述可以直接整件商品编码。Embedding 模型把每段文字转成向量，Milvus 保存向量以及 itemId、chunkId、版本和必要过滤字段。分块不是越细越好，否则“防水但不适合暴雨”被拆开后可能丢失限制条件。

同一商品可能有多个 chunk，因此查询结果需要按商品合并，避免前十名全是同一件商品。商品更新、下架和删除需要同步更新检索资料，模型版本变更也需要考虑重建，不能一直混用不兼容的向量。

#### 聊天检索与 agent 工具调用

用户需求由助手整理为搜索文本和结构化条件，例如类别和价格上限。搜索工具调用 Milvus 查相关商品，也可以结合已有关键词搜索能力；工具返回真实 itemId，再调用商品接口取得当前价格和状态。语言模型根据这些结果说明为什么适合，用户追问时重新检索或比较，而不是凭语言模型记忆制造商品。

Agent 在这里是能够选择并调用明确工具的对话组件，不是直接拥有数据库管理员权限的机器人。搜索、详情和比较是不同工具职责；只读推荐不需要获得扣库存或付款权限。上下文要区分不同用户和会话，并限制输入长度和调用次数，避免无限循环和成本失控。

#### 错误结果 安全与效果验证

向量相似度不能保证满足所有硬条件，例如 100 美元以内必须用过滤或最新数据检查落实。没有合适商品应明确告知，而不是为了给出回答编造一个。商品介绍中的指令属于不可信输入，不能让它改变助手权限或执行额外操作。

Milvus 不可用时可以回退普通搜索，模型不可用时可以显示实际商品卡片。评估包括相关商品是否出现在前几名、错误推荐率、实时价格正确性、响应延迟和每次会话成本。提升点击或转化需要业务实验，不能从“接入向量数据库”直接推导。

#### 英文回答

“I built a conversational shopping feature so customers can describe what they need and refine it through follow-up questions. Product descriptions are converted into embeddings and stored in Milvus for semantic search. The agent calls search and product-detail tools, then explains suggestions using current catalog information. It does not invent products or use vector similarity as proof that stock and price are still valid.”

### 学习顺序与交叉追问

先把一次完整购买讲通，再回到每个 bullet 的技术细节：用户身份怎样建立，商品怎样查询，库存怎样预留，订单和付款怎样记录，以及失败后怎样继续。搜索和 AI 推荐属于发现商品的路径，AWS 和 Kubernetes 属于运行环境，测试则验证所有路径的规则。这样面试官从一个主题跳到另一个主题时，你仍然在解释同一个系统，而不是背 14 段互不相关的答案。

英文回答是练习模板，不必逐字背诵。每条至少准备一个正常例子、一个失败例子和一种验证方法；涉及实际经历时，使用自己确实实现或验证过的细节。如果讲到目前未做的部分，应明确是在解释方案，而不是把完整目标版本当成部署记录。
