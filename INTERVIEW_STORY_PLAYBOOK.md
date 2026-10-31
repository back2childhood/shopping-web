# Shopping Platform — Interview Playbook: What, Why, How and Project Stories

这份文档围绕购物项目准备面试，每个技术主题先解释“是什么”，再解释“为什么用”和“项目里怎么用”，然后展开 ticket 故事、故障处理与验证结果。英文部分采用完整、自然的口语段落，中文部分解释容易混淆的概念；与 [英文 V2](INTERVIEW_QA_V2.md) 和 [中文 V2](INTERVIEW_QA_V2_CHINESE.md) 配合学习。

**使用口径：本次以你提供的 Jay Qian_u_TP.doc 的经历描述为回答基线，ticket 情境、经理讨论和容量计算仍是练习材料。** 简历写明 7+ 年经验，最新项目为 Wolf & Badger（Jan 2025–Present）；没有写正式团队名、百万日活、具体压测结果或最新项目的 CI/CD 工具。因此不再把 Order Experience Team 当成已确认组名，也不把架构模型中的数字放进自我介绍当成履历事实。简历内容用于对齐你的自述，不是独立背景验证。

## 目录

1. [3–5 分钟英文逐字介绍](#intro)
2. [回答顺序：What、Why、How、Story、Failure、Result](#method)
3. [Kafka 与 Consumer Lag：基础、使用和积压处理](#lag)
4. [Kafka Producer：Batch、Linger、Buffer 与可靠性](#producer)
5. [Kafka Consumer：Offset、提交与幂等消费](#commit)
6. [Kafka Dead-Letter：失败消息、重试与重放](#dlq)
7. [Transaction：ACID、本地事务与项目使用](#local-tx)
8. [Transaction Propagation：七种传播行为](#propagation)
9. [Distributed Transactions：Outbox、Saga 与幂等](#saga)
10. [Spring Security 与 JWT：认证、权限与刷新](#security)
11. [Multithreading：线程池、CompletableFuture 与下单](#threads)
12. [模拟追问与练习方法](#practice)
13. [简历建议与需要统一的口径](#resume-review)
14. [最新项目十四条职责对照](#resume-map)
15. [Redis Cache：缓存内容、失效与调试](#cache-story)
16. [Elasticsearch：关键词搜索与索引同步](#search-story)
17. [AI Shopping Assistant：Embedding、Milvus 与 Agent](#ai-story)
18. [AWS、Docker、Kubernetes 与 CI/CD](#deployment-story)
19. [不同项目的经历如何区分](#career-boundaries)

<a id="intro"></a>
## 1. 3–5 分钟英文逐字介绍

下面按简历使用 seven-plus years 和 Wolf & Badger。`[actual team name]` 保留为真实团队名称占位符；若没有正式小组名，就描述工作范围，不发明公司内部名称。介绍重点是 Kafka 事件处理和多线程优化，安全只简要交代，具体 ticket 集中讲结账时并发读取商品详情；英文稿按正常语速练习约 3–5 分钟。

### English script

I’m Jay, and I have seven-plus years of experience in backend development across e-commerce, user engagement, and payment-related systems. Since January 2025, my latest project has been an e-commerce platform for Wolf & Badger. Customers use it to find products, place orders, make payments, and follow their purchases. Sellers use it to manage their products, prices, inventory, and order fulfillment.

I work in [actual team name], supporting the checkout and order backend. My main responsibilities are Kafka event processing between services and multithreading improvements in the checkout flow. The team needs both because customer requests should respond promptly, while the updates they trigger still need to reach other services reliably.

On the Kafka side, I work on producers and consumers for product, order, and inventory updates. My work includes reliable event publishing, duplicate-event handling, retries, and recovering failed messages. I also monitor consumer lag to identify when downstream processing falls behind. For example, a seller's product update needs to reach the catalog and search services without making the seller wait for both services to finish during the original request.

On the multithreading side, I focus on independent work that can run concurrently, especially product-detail lookups during checkout. I manage the thread-pool limits, waiting time, and failure handling so that faster checkout does not overload Item Service. I also test slow responses and overloaded queues, not just successful requests.

For security, the application uses JWT to identify authenticated users and separates buyer and seller permissions. The Gateway validates incoming authentication, while business services check ownership. A buyer can access only their own orders, and a seller can modify only their own products.

One ticket that represents my work was improving checkout for carts with multiple products. The original flow requested each product's details one at a time. I traced that stage and found that it spent time waiting for independent network responses, so a larger cart accumulated unnecessary waiting before inventory reservation could begin.

I compared a batch product API with parallel calls to the existing API. A batch endpoint could reduce the number of calls, but it required a coordinated interface change. After discussing the scope and downstream capacity with my manager and the catalog team, we chose bounded parallel reads for this ticket and kept the batch endpoint as a separate improvement.

I used CompletableFuture with a dedicated thread pool and a bounded queue. I added individual request timeouts and an overall waiting deadline, and required all necessary lookups to succeed before starting inventory operations. I kept stock deductions and payment calls outside this parallel stage because those operations change business data and need their own recovery rules.

The change allowed independent reads to overlap instead of waiting one by one, without increasing the number of product calls per order. I evaluated it through checkout latency, queue wait, rejected tasks, and Item Service response time under the same load. That ticket reflects my main focus: using concurrency to remove unnecessary waiting while keeping service capacity and failure behavior under control.

### 如果对方打断，怎样接下去

面试官追问 Kafka lag、提交或死信恢复时，接第 3–6 节；追问线程池大小、CompletableFuture、队列满了怎么办时，接第 11 节。职责介绍同时覆盖 Kafka 和多线程，但 ticket 只围绕多线程展开，不要中途换成支付或 Saga 的另一个故事。具体延迟改善仍应使用可核实的测量结果，不把理论等待时间的变化当成整条 checkout 的实测提升。

<a id="method"></a>
## 2. 每个技术主题的回答顺序

面试官问“Do you know transactions?”时，先用两三句解释 transaction 是什么，再说明它为什么出现在订单或商品流程里。只有对方已经问了具体事故，才可以直接从故障背景开始。不要在对方还没理解技术职责的时候，一口气讲完事件表、重试次数和经理讨论。

本次每个技术章节都按同一学习顺序展开。英文段落是可以连续说出来的回答，中文用于解释误区和补充边界；练习时不必把全部内容一次背完。先讲定义、业务原因和基本实现，随后按追问进入 ticket、失败处理和结果。

| 回答阶段 | 要回答的具体问题 | 项目中的示例 |
|---|---|---|
| What is it? | 它是什么，负责解决哪一类问题？ | Transaction 把相关数据库操作作为一个工作单元。 |
| Why do I use it? | 哪个业务流程需要它，不用会怎样？ | 商品已保存但库存没保存，会留下半成品记录。 |
| How do I use it? | 哪个服务、哪些记录、什么边界和设置？ | Item Service 将商品、库存和 outbox 放入同一个本地事务。 |
| Ticket story | 当时的问题、备选方案，以及跟经理决定了什么？ | 比较失败后清理与本地事务，确定保存和异步展示的不同承诺。 |
| What if it fails? | 宕机、超时、重复、并发时结果怎样？ | 本地回滚不撤销远程库存，跨服务需要恢复流程。 |
| Result and numbers | 如何验证，数字衡量的是什么？ | 故障注入验证不残留部分记录，而不是声称事务加快了接口。 |

故事仍然重要，但它现在用来解释前面的技术选择，而不是代替概念介绍。经理讨论要有具体决定，例如接受多长的浏览数据延迟、先做批量接口还是并发读取，以及支付未知时给客户显示什么；不要只说“经理同意了我的方案”。

### 本文统一的练习口径

Kafka 采用传统 consumer group 的分区分配模型。商品事件练习用八个分区、RF=3、min ISR=2，输入五百条事件每秒；线程池练习用四个 Order 实例、每实例八个 lookup worker、三十二个排队任务，五十单每秒且每单平均四件。它们是不同路径的容量例子，不应把 HTTP RPS、Kafka events/s 和同时在途请求数当成同一个指标。

参数不是测量结果。本文可以用计算解释为什么选择有界并发，或者用故障测试条件解释怎样验收，但不会从一组估算推出某家公司实际有多少用户、多少工程师或提高了多少销售额。练习中的完整架构也不等于 Wolf & Badger 公开确认的内部实现。

Kafka 回答始终考虑 producer、broker 和 consumer 三个角度。特别注意，acks 是 producer 配置；min.insync.replicas 属于 broker/topic 侧可靠性设置；扩容同一处理任务是在原 consumer group 内增加成员，而不是创建更多 group。事务章节则明确区分本地事务、传播、隔离、Outbox 和 Saga。

<a id="lag"></a>
## 3. Kafka 与 Consumer Lag：先讲它是什么，再讲为什么积压

### What is it? — 英文开场

Kafka is an event-streaming platform. A service publishes a record of something that happened, such as a product update, and other services read that record when they are ready. Kafka keeps records in topics, and each topic is divided into partitions so that storage and processing can be spread across machines.

A producer writes events, brokers store them, and consumers process them. Consumers that share one job belong to the same consumer group. For example, catalog consumers update Cassandra product views, while a separate search group updates Elasticsearch from the same product events. Those groups work independently.

Consumer lag is the distance between a consumer group's progress and the available records in a partition. It tells me that work is waiting, but not automatically why it is waiting. I also check how old the waiting events are, because a thousand old payment events can be more serious than a larger, short-lived analytics backlog.

### Why do I use it? — 对应什么业务问题

In the shopping project, one product update can affect catalog pages, search results, and other downstream features. If Item Service called every downstream service before answering the seller, a slow search service could delay a simple product edit. Kafka allows Item Service to record the change reliably and lets each downstream service process it independently.

The trade-off is that those views are not updated at exactly the same moment. That is acceptable for browsing within a defined freshness target, but I do not use an old search result as the final authority for a payment amount or inventory reservation.

### How do I use it? — Producer、Broker、Consumer 三个角度

On the producer side, Item Service records an outbox event with the product change and later publishes it with itemId as the key. The stable key keeps one item's events in the same partition while the partition mapping stays unchanged. I also include eventId and version so consumers can identify duplicates and outdated changes.

On the broker side, the exercise uses eight partitions, a replication factor of three, and a minimum of two in-sync replicas. On the consumer side, the catalog group starts with four consumer instances. Each instance receives approximately two partitions in a balanced assignment, and a separate search group receives its own copy of the event stream.

These are different kinds of scaling. Adding members to the existing catalog group shares its work; creating another group creates another independent subscription. A traditional consumer group cannot assign the same partition to two active members at once.

### Ticket story — 为什么排查、怎样取舍

The ticket starts when sellers report that saved product changes take too long to appear to buyers. I first compare update timestamps with the catalog group's event age. Then I inspect producer bursts and outbox delay, broker disk and replication health, and consumer database latency, retries and partition distribution.

I discuss two options with my manager: increase processing capacity, or reduce unnecessary work per event. We agree to restore catalog freshness without weakening delivery guarantees. If Cassandra is already saturated, adding consumers would make the situation worse, so I first remove repeated queries or limit nonurgent imports. If spare database capacity exists, I can add consumers inside the existing group.

### What if it fails? — 为什么加 consumer 不一定有效

If only one partition is slow, the cause may be a popular key or a poison message. More consumers cannot split that one partition under this model. If every partition is slow, I check shared dependencies and processing cost; if committed lag is high but processing is current, I inspect the commit policy before declaring a business backlog.

I do not solve lag by committing offsets before work finishes. That makes the chart look better while making a restart capable of skipping unfinished updates. Retries, dead-letter handling and offset commits are explained separately below.

### Result and numbers — 如何计算与验证

Suppose events arrive at 500 per second and four consumers together process 400 per second. The backlog grows by 100 events per second. If eight consumers can process 800 per second without saturating Cassandra, there are 300 events per second left to drain old work, and a backlog of 180,000 events takes about 600 seconds to clear.

这是容量计算，不是实际吞吐承诺。验收要同时看积压年龄下降、商品版本追上、数据库延迟稳定和没有重复业务影响；不能只看 consumer 数增加。20 个分区配 5 个同组消费者，大致每人 4 个分区，但分到相同数量不代表负载相同。

<a id="producer"></a>
## 4. Kafka Producer：Batch、Linger、Buffer 与可靠性

### What is it? — 英文开场

A Kafka producer is the client that sends events to Kafka. It normally collects records in memory and sends batches rather than opening a separate network exchange for every record. Batching improves efficiency because several events share request overhead.

Three useful controls are batch.size, linger.ms and buffer.memory. The batch size is a byte target for a partition batch; linger allows a short wait for more records; buffer memory provides space for records waiting to be sent. A buffer absorbs temporary bursts, but it does not provide durable storage.

### Why do I use it? — 为什么需要调这些参数

In the shopping project, seller imports can create many product events at once. Sending one event and immediately waiting for its result before sending the next prevents useful overlap. The purpose of tuning is to make catalog updates reach downstream services promptly, without losing events or creating an unlimited memory backlog.

I do not maximize batch size without considering latency. Large, full batches are efficient, but a lightly used partition may wait to collect them. I choose a small waiting budget and measure both throughput and event age.

### How do I use it? — 发送流程和起始参数

For this exercise, I start with a 64 KiB batch size, five milliseconds of linger, a 32 MiB producer buffer, and lz4 compression. I reuse the producer and allow a bounded number of sends to overlap across independent items. I preserve the publication order of versions for the same item.

KafkaTemplate.send returns a CompletableFuture because the broker acknowledgment arrives later. I use that completion result to decide whether publication succeeded; calling send alone does not mean the record is safely stored. The outbox row is marked published only after confirmation, and an error leaves it eligible for retry.

For reliability, I explicitly enable producer idempotence and use acks=all. That is a producer setting; replication factor and min.insync.replicas are broker/topic-side decisions. With replication factor three and minimum ISR two, writes using acks=all fail when the ISR falls below two instead of accepting a weaker copy count.

### Ticket story — 怎样和经理讨论优化

The ticket is delayed catalog updates during a product import. I compare changing producer settings with fixing the outbox publisher's one-at-a-time waiting. My manager and I agree to remove the serial bottleneck first, while keeping the reliability policy unchanged.

I then test moderate batching and compression and watch broker request latency and downstream lag. Faster publication is not a complete success if it only moves the backlog from PostgreSQL to Cassandra consumers. We judge the full time from the seller's save to a refreshed product view.

### What if it fails? — Buffer 满了、Broker 不可用怎么办

If broker communication stalls, the producer buffer eventually fills. A send may block while waiting for buffer space or metadata and can fail after its configured wait limit. I surface that failure to the publisher and keep the pending outbox record; I do not silently drop the event.

Producer idempotence helps prevent duplicates from the producer's own retries, but it does not deduplicate an outbox event that the application sends again after a restart. Consumers still use stable business/event identifiers. Also, acks=all means acknowledgment by the current ISR, not necessarily all three configured replicas regardless of their health.

### Result and numbers — 性能收益怎样解释

If every event waits twenty milliseconds for acknowledgment before the next send starts, that loop has a rough ceiling of fifty sends per second. Bounded asynchronous sending removes that artificial serial limit, while batching reduces request overhead. The actual improvement needs a measured comparison of events per second, batch utilization, publication age and buffer wait.

这里的“异步”不是“不用管结果”。它只是让等待不阻塞下一项独立工作，确认失败仍然必须恢复；生产者 buffer 也不能代替 outbox。参数语义见 [Kafka producer 配置](https://kafka.apache.org/41/configuration/producer-configs/)。

<a id="commit"></a>
## 5. Kafka Consumer：Offset、提交与幂等消费

### What is it? — 英文开场

An offset is a record's position within one Kafka partition. Committing an offset saves the consumer group's restart position. It does not delete the record, and it does not prove that a database update or payment succeeded.

For example, after successfully handling the record at offset ten, the group normally commits eleven, which means the next record to read is eleven. Each partition has its own position. There is no single global offset that represents every partition in a topic.

### Why do I use it? — 为什么关心提交时间

In the shopping project, a consumer may update an order and then restart. If it commits the offset before the update succeeds, the update can be skipped after restart. If it updates the database first and crashes before committing the offset, Kafka may deliver the event again.

I prefer the second situation because a repeated event can be made safe, while a skipped business update is harder to discover. That requires an idempotent consumer: processing the same event again must not repeat the business effect.

### How do I use it? — 先业务提交，再确认消费

The listener calls a separate transactional service. That service inserts an inbox entry identified by consumer name and eventId, applies the allowed order-state change, and commits both in the same PostgreSQL transaction. If the event was already completed, it returns without applying the change again.

Only after that transaction commits does the listener acknowledge the record. For this exercise, I use enable.auto.commit=false, a synchronous record listener, MANUAL_IMMEDIATE acknowledgment on the consumer thread, and syncCommits=true. This makes the boundary easy to explain and test, although it is not necessarily the highest-throughput configuration.

The unique inbox constraint handles two concurrent deliveries as well as a later retry. The insert uses a conflict-safe approach such as INSERT ON CONFLICT DO NOTHING. I do not catch a uniqueness exception inside a failed PostgreSQL transaction and then assume that transaction is still usable.

### Ticket story — 怎样选择提交方式

The ticket is to make order events safe across restarts. I compare automatic periodic commits, Spring-managed processing-aware commits, and explicit acknowledgment. With my manager, I choose an easily verified processing boundary first and leave batching commits as a separate performance improvement.

The key decision is not simply “manual is safer.” Safety comes from knowing when business work has committed, how errors are retried, and what happens when the same event returns. Spring-managed RECORD or BATCH acknowledgment can also be appropriate when their processing semantics fit the listener.

### What if it fails? — 三个容易混淆的概念

Kafka auto-commit decides whether the client periodically saves positions. Spring AckMode decides when the container considers records ready to commit. Synchronous versus asynchronous commit decides whether the commit call waits for broker confirmation. These are separate controls, so manual acknowledgment does not automatically mean asynchronous commit.

With commitSync, the caller waits for a result and handles failure. With commitAsync, the caller proceeds and observes failure through a callback; it must not blindly resubmit an old position after a later commit has succeeded. I also avoid handing records to arbitrary workers and committing past unfinished records.

### Result and numbers — 怎样证明没有漏处理

I test a crash before database commit and a crash after database commit but before offset confirmation. The first case must retry the unfinished work; the second may replay the event but must not repeat its effect. Sending the same event ten times should produce one completed business change for that consumer.

不能把这称为“Kafka listener 只运行一次”。这里追求的是重复投递下业务效果只发生一次；Kafka 自身事务也不会自动包住 PostgreSQL 或支付商。提交方式可对照 [Spring Kafka 容器说明](https://docs.spring.io/spring-kafka/reference/kafka/receiving-messages/message-listener-container.html)。

<a id="dlq"></a>
## 6. Kafka Dead-Letter：失败消息为什么不能只重试

### What is it? — 英文开场

A dead-letter destination stores messages that normal processing could not complete. People often call it a DLQ; in Kafka, it is usually a dead-letter topic, or DLT. It keeps the original event and useful failure information so the team can investigate and recover it.

A dead-letter topic is not a successful business outcome. Moving a payment event there does not complete the payment workflow. It means the normal path could not finish and further action is required.

### Why do I use it? — 为什么不能无限重试

In the shopping project, an invalid product event could repeatedly fail and delay valid updates behind it. A temporary database timeout may disappear on retry, but a message with an unsupported schema will not repair itself. Treating both failures the same wastes capacity and hides the records that need attention.

I use bounded retries for recoverable failures and isolation for messages that need a code or data correction. The trade-off is that later events may move ahead, so order-sensitive workflows need additional rules rather than blindly continuing.

### How do I use it? — 从失败到重放的完整流程

For a transient error, the example policy allows three retries after the first attempt, with delays of one, two and four seconds. That is four attempts in total. A clearly invalid contract goes directly to the DLT instead of consuming the retry budget.

The DLT record contains the original payload, eventId, business key, schema version, source topic, partition and offset, plus a safe error description. In Spring Kafka, an error handler and DeadLetterPublishingRecoverer provide the plumbing, but I explicitly verify that failed DLT publication is reported as a failure. I do not advance source progress as if recovery succeeded when the DLT write failed.

Replay is a controlled operation. After fixing the consumer or correcting the approved data issue, I select the affected records, test a sample, and republish them with their original event identity. The consumer checks duplicates and versions again; replay does not bypass business validation.

### Ticket story — 经理讨论的是什么

The story starts with a catalog deployment that cannot read one event's schema version. I compare blocking the entire partition until a fix is deployed with isolating the bad event and continuing independent product updates. With my manager, I agree on an alert, a recovery owner, and the conditions under which continuation is safe.

For a payment sequence, I would be more restrictive. If an earlier event is necessary for a later transition, the order stays pending or needs reconciliation. We do not trade an apparently healthy lag chart for an incorrect order state.

### What if it fails? — 重试与乱序问题

Blocking retries delay the affected consumer's progress, so their total duration must fit the polling and liveness configuration. Retry topics avoid long blocking waits but can let later events overtake the failed one. Neither mechanism automatically preserves the original business order through failure.

If version twelve failed and thirteen already updated a full product snapshot, replaying twelve must not overwrite thirteen. If the messages represent required incremental operations, a version gap needs recovery rather than merely discarding the older event. Deserialization failures also need handling before the business listener can run.

### Result and numbers — 恢复完成的标准

The exercise uses a thirty-day DLT retention window and starts approved replay at fifty records per second. I watch successful recovery, repeat failures, downstream load and outstanding business records. The work is complete when the missing product changes or order transitions are resolved, not just when the DLT is empty.

Producer 负责避免继续发送不兼容数据，broker 为 DLT 提供持久化与权限，consumer 负责分类、重试、告警和恢复。这三个角度都要讲，但故事重点是“坏消息怎么被发现，谁来修，修完怎样安全重放”。实现边界见 [Spring Kafka 错误处理](https://docs.spring.io/spring-kafka/reference/kafka/annotation-error-handling.html)。

<a id="local-tx"></a>
## 7. Transaction：是什么、为什么用、项目里怎么用

### What is it? — 先给面试官一个完整定义

A transaction groups database operations into one unit of work. Instead of treating each write as an independent success, the database commits the group or rolls it back if the operation cannot complete. In my shopping example, creating a product involves saving its details, its initial inventory, and an outbox event in the same PostgreSQL transaction.

Transactions are described by ACID: atomicity, consistency, isolation and durability. I explain those properties through this product workflow, because just expanding the four letters does not explain what they protect.

**Atomicity.** All the writes inside this transaction succeed together or are rolled back together. If saving the initial inventory fails, the product and its pending event should not remain committed. This guarantee covers the participating database operations, not an external HTTP request or Kafka publication.

**Consistency.** A transaction should move the database from one valid business state to another. For example, a reservation must have a positive quantity, available stock must not become negative, and an order total must follow the pricing rules. Database constraints and application logic enforce these rules; a transaction does not invent them or automatically make a wrong calculation correct.

**Isolation.** Isolation controls how concurrent transactions interact and what data they can observe. In this example, I use PostgreSQL Read Committed together with conditional updates and uniqueness constraints. Read Committed prevents dirty reads, but it does not mean two requests cannot compete for the last item.

**Durability.** After a successful commit, the database preserves the changes according to its durability configuration. PostgreSQL normally relies on write-ahead logging and durable log flushes for crash recovery. Backups, replication and restore procedures address broader failures; durability does not mean every possible storage disaster is automatically covered.

### Why do I use it? — 为什么产生这张 ticket

When a seller creates a product, saving only the item row is not enough. The product needs initial stock and a reliable record of the catalog change. If each insert commits separately, the application can fail after saving the product but before saving the inventory or event.

That is why I place these related PostgreSQL writes in one local transaction. I do not include Cassandra or Redis in that transaction. They are updated through the committed event afterward, and I explain that a successful seller save and a refreshed browsing view are separate moments.

The same principle applies elsewhere: an order and its lines belong in one local order transaction; a stock change and its reservation record belong in one inventory transaction; a payment result and its outgoing event belong in one payment transaction. Each transaction stays within the service's participating database resources.

### How do I use it? — Spring 到底替我做了什么

I put @Transactional on the application service method that owns the complete database operation, rather than relying on independent repository calls. The method is called through a Spring-managed proxy, and all participating repositories use the intended transaction manager. Spring arranges the transaction before the method runs and completes it afterward.

In simple terms, the proxy is a wrapper around my service. The caller reaches the wrapper first; it starts or joins a transaction, invokes my business method, and then commits or rolls back according to the outcome. This is an example of Spring AOP: transaction handling surrounds the business code without being repeated inside every method.

I let errors propagate to that boundary. By default, Spring rolls back for unchecked exceptions and Errors; I configure rollback rules for checked exceptions that should also cancel the operation. I also remember that JPA save may not execute every SQL statement immediately, so failure can appear at flush or commit rather than at the save call.

下面是边界示意，不是完整项目实现。三个写入必须使用同一个 PostgreSQL 事务管理器；outboxService 是另一个 Spring bean，它只插入事件记录，不在这里发送 Kafka 消息。

~~~java
@Transactional
public Item createItem(CreateItemRequest request) {
    Item item = itemRepository.save(buildItem(request));
    inventoryRepository.save(buildInitialInventory(item, request));
    outboxService.enqueue(buildItemCreatedEvent(item));
    return item;
}
~~~

### Ticket story — 怎么考虑方案、跟经理决定范围

The ticket is preventing partially created products. I compare a local transaction with cleanup code that deletes earlier records when a later insert fails. Cleanup introduces another operation that can also fail, while all three writes already belong to one database, so a local transaction is the simpler boundary.

I explain to my manager that this guarantees an all-or-nothing seller write, but it does not make every downstream view immediately current. We agree to preserve the source of truth first and monitor catalog publication delay separately. That keeps the business promise precise.

### What if it fails? — 为什么加注解仍可能不正确

If a method calls another annotated method on the same object, the internal call can bypass the transaction proxy. If I catch an exception and return normally, the transaction may commit unless it has already been marked rollback-only. If I start a worker thread, the ordinary thread-bound transaction does not automatically follow it.

For stock, I avoid reading a number into Java, subtracting it, and saving it without concurrency protection. I validate a positive quantity and use an atomic conditional update. The database must report one updated row before the reservation can succeed.

~~~sql
UPDATE inventory
SET available = available - :quantity
WHERE item_id = :itemId
  AND available >= :quantity;
~~~

这个语句解决“库存够不够”的竞争，不解决重复请求。还要把 reservation 唯一记录与扣减放入同一事务，重复 reservation 不能再次扣减；这与支付幂等、Saga 的关系见第 9 节。Read Committed 下竞争 UPDATE 会等待并重新判断条件，但跨多行规则仍需要合适的锁、条件或更严格隔离，不能泛化成所有业务都安全。

### Result and numbers — 怎样证明用了事务有价值

I inject a failure after each write and verify that no partial product, inventory or outbox remains committed. I also test ten available units against one hundred concurrent requests for one unit: successful reservations must not exceed ten, and stock must not become negative. Those are correctness checks, not evidence that transactions make requests faster.

截图中的两句话需要改准确：Read Committed 不是“secure the database”，而是控制并发可见性；durability 也不是笼统的“database consistency configuration”，而是日志持久化和恢复机制。隔离细节见 [PostgreSQL 文档](https://www.postgresql.org/docs/current/transaction-iso.html)，代理边界见 [Spring @Transactional](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html)。

<a id="propagation"></a>
## 8. Transaction Propagation：七种行为怎样结合项目解释

### What is it? — 和 Isolation、Outbox 分开

Transaction propagation defines what happens when a transactional method calls another transactional method. The question is whether the second method joins the current transaction, starts an independent one, runs without one, or rejects the call. It is about transaction participation across method boundaries, not sending events between services.

Isolation answers a different question: what can concurrent transactions see or change? Outbox also answers a different question: how do we save business data and the intention to publish an event together? They are related design choices, but they are not three names for the same mechanism.

### Why do I use it? — 为什么不能每个方法都新开事务

In Item Service, product creation calls inventory code and OutboxService. Those writes must succeed together. If OutboxService starts an independent transaction and commits first, it can leave a product-created event behind even if product creation later rolls back.

The later publisher has a different job. It handles events whose business transaction has already finished, so its progress updates should use short transactions of their own. I choose propagation according to which records must share a commit, rather than using the same annotation everywhere.

### How do I use it? — 七种模式完整对照

| Propagation | What it means | Shopping-project explanation |
|---|---|---|
| REQUIRED | Join the current transaction, or create one if none exists. | The outer product-creation method establishes the shared transaction. |
| SUPPORTS | Join an existing transaction, but do not require one. | An optional helper read can participate in its caller's transaction. |
| MANDATORY | Require an existing transaction; otherwise fail. | Outbox enqueue must be part of a business transaction. |
| REQUIRES_NEW | Suspend an existing transaction and start an independent one. | A deliberately independent persistence step may commit separately. It is not suitable for the product's coupled outbox insert. |
| NOT_SUPPORTED | Suspend an existing transaction and run without it. | A boundary can exclude work from transaction participation, although I prefer arranging remote waits outside transactions from the start. |
| NEVER | Fail if a transaction exists; otherwise run without one. | A guard can detect an unexpected transactional caller. |
| NESTED | Use a savepoint inside an existing transaction when supported; otherwise start a transaction if none exists. | A JDBC import may roll back one subsection while retaining earlier work, subject to the transaction manager's support. |

For the product example, the outer method uses REQUIRED and OutboxService.enqueue uses MANDATORY. Both calls cross Spring bean boundaries. MANDATORY catches an incorrect caller that tries to enqueue a business event without the matching transaction.

The publisher runs later and can start an ordinary REQUIRED transaction when it has no caller transaction. I do not need REQUIRES_NEW merely because the work is in another method or runs later. REQUIRES_NEW matters when I deliberately need independence from an existing transaction.

### Ticket story — 为什么选择 MANDATORY

The ticket appears when several operations begin sharing OutboxService. I compare REQUIRED, which can silently create a transaction for an accidental standalone call, with MANDATORY, which rejects that call. I discuss with my manager whether an orphan event should be treated as programmer misuse, and we make that contract explicit.

The change is small, but its purpose is important: future callers should not be able to bypass the intended atomic boundary without noticing. We add tests for both a valid transactional caller and an invalid standalone caller.

### What if it fails? — 最常见的深入追问

If an inner REQUIRED operation marks the shared transaction rollback-only, catching its exception outside does not necessarily rescue the transaction. The final commit can throw UnexpectedRollbackException. I fix the transaction design instead of assuming that catching Java exceptions resets database state.

REQUIRES_NEW may need another database connection while the outer transaction still holds resources. It can commit independently, but an exception from it can still propagate and cause the outer method to fail. NESTED is different: a successful nested step is still undone by an outer rollback, and JPA does not automatically provide all JDBC savepoint semantics.

### Result and numbers — 不硬套性能百分比

I test that the product and outbox roll back together, that enqueue without a transaction fails, and that independent publisher progress can be recorded later. The result is a clearer correctness boundary, not a claimed latency reduction. If nested transactions are considered, I also test the actual transaction manager instead of assuming support from the annotation alone.

这七种是 propagation behaviors，不是七个 isolation levels。截图中 Outbox 那段应放到“可靠事件发布”里，不能作为 transaction propagation 的定义。可查 [Propagation 枚举](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/annotation/Propagation.html)与 [Spring 传播边界](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html)。

<a id="saga"></a>
## 9. Distributed Transactions：Outbox、Saga 与幂等分别解决什么

### What is it? — 三个概念先讲清

A distributed business operation changes data owned by more than one service. Checkout is an example: Order creates the purchase, Inventory reserves stock, and Payment contacts a payment provider. One local database transaction cannot automatically roll back all those independent systems.

A Saga coordinates a sequence of local transactions. Each step records its result, and if the overall operation cannot finish, the workflow performs a business action that compensates for an earlier step. Releasing reserved stock is compensation; it is a new operation, not a database rollback across services.

An outbox is a database table for pending events or commands. Saving business data and the outbox row in the same local transaction prevents a committed change from losing its publication intent. Idempotency makes retries safe by recognizing a previously requested operation instead of performing it again.

### Why do I use it? — 一次下单为什么需要这些

The project needs to recover when inventory succeeds but the next step fails or times out. Keeping Order Service's database transaction open does not make the remote stock reservation part of that transaction. It only holds resources longer.

The payment provider may also charge successfully while its response is lost. A timeout therefore means the result is unknown, not necessarily that payment failed. We need a durable workflow that can check the original attempt and finish or compensate it without issuing another charge.

### How do I use it? — Outbox 的具体作用

For a product change, Item Service commits the item, inventory change if applicable, and outbox event together. A publisher later claims eligible outbox rows, sends them to Kafka, and records confirmed publication. When several publishers run, claiming needs coordination, such as short database claims with expiry and ownership checks; workers must not publish conflicting item versions arbitrarily.

If Kafka is unavailable, the committed outbox row remains pending. If Kafka accepts the event but the publisher crashes before recording success, the row may be published again. That is why outbox gives recoverable delivery, not an excuse to remove consumer deduplication.

### How do I use it? — Saga 与业务幂等的具体作用

For checkout, Order stores a pending order and an inventory command in one local transaction. Inventory processes that command using a stable reservation identity, such as orderId plus itemId, and stores the reservation and conditional stock change together. A duplicate command returns the existing result after verifying the requested quantity matches.

Payment similarly stores an attempt identified by a scoped Idempotency-Key and a request fingerprint. The fingerprint represents important request fields such as order, amount and currency. Reusing the same key with different request content is rejected; reusing it for the same request refers to the original attempt.

The provider call occurs outside the local transaction and uses a stable provider reference where the provider supports idempotency. A timeout leaves the attempt in a pending or unknown state until a callback or status query resolves it. If provider idempotency or query support is absent, I cannot claim that a local unique constraint alone prevents a second external charge.

Order consumes results and moves its workflow through allowed transitions. I keep order progress, payment progress and inventory reservation status separate because an order can be paid while its inventory is still reserved. Cancellation creates an idempotent release command; a late successful payment may require an idempotent refund instead.

### Ticket story — 业务决定比模式名称重要

The ticket is a checkout that reserves stock and then loses the payment response. I compare immediately cancelling, retrying with a new reference, and preserving the original payment attempt while checking its result. The first two choices can create stock or charging problems.

With my manager and the payment owner, I agree on how long an order may remain pending and what happens if a successful payment arrives after cancellation. We choose explicit pending status and recovery rather than a fallback that invents a final outcome. The Saga records progress so a process restart does not erase that decision.

### What if it fails? — 补偿也会失败

A release can be delivered twice, so only the first valid RESERVED-to-RELEASED transition returns stock. A confirmation racing with a release must be resolved by conditional state changes, not by two unconditional updates. Refunding money also does not automatically mean a shipped item is back in sellable inventory.

If compensation fails, the workflow records pending recovery and retries safely, with alerts and manual review for unresolved cases. A consumer ignores a duplicate event only after verifying that its business effect was completed, not just because a Redis key temporarily exists.

### Result and numbers — 怎样验证恢复能力

I test twenty identical payment requests and expect one payment attempt and one provider-side charge under the provider contract. I stop a worker after inventory commits but before Order records success, then restart with the same reservation identity. I also test payment success racing with cancellation and verify that any required refund remains visible until resolved.

这里的收益是减少丢事件、重复扣款和人工找单的风险，不该凭空写成“提高 50% 性能”。Outbox 负责数据库与发消息之间的缺口，Saga 负责跨服务业务恢复，幂等负责重复执行；三个机制互补，谁都不能单独替代另外两个。

<a id="security"></a>
## 10. Spring Security 与 JWT：认证、权限和会话更新

### What is it? — 英文开场

Spring Security is a framework for controlling access to an application. Authentication verifies who the caller is, while authorization decides what that caller may do. In the shopping project, logging in establishes the user's identity, but editing a product also requires permission over that particular product.

JWT is a token format that carries claims such as user ID, roles and expiration. A signed JWT lets a service verify that trusted claims have not been altered. The claims are normally readable, so I do not put passwords, payment details or other secrets inside the token.

### Why do I use it? — 为什么不只靠前端或 Gateway

The browser can hide seller buttons, but a user can still call the API directly. The Gateway can check a token, but it does not automatically know which seller owns a product or which buyer owns an order. That is why I use both request authentication and business-level ownership checks.

A short-lived access token also avoids sending the password on every API request. A separate refresh mechanism lets users continue a session without making the access token valid indefinitely.

### How do I use it? — 登录和访问是两个流程

During login, Auth Service looks up the credential record using the account's stable user ID and verifies the password through a PasswordEncoder. Passwords are hashed, not decrypted for comparison. Successful login issues a signed access token containing sub, roles, issuer, audience and expiration.

For protected requests, the client sends the token as a bearer credential. Spring Security's resource-server support validates the signature with trusted verification keys and applies the configured issuer, audience and time checks. A role converter maps the token's role claim to the application's authorities. The Gateway uses a reactive security chain, while MVC services use a servlet security chain.

In Item Service, I check both the SELLER role and the stored sellerId. An update can include item ID and authenticated owner ID in its database predicate so authorization does not rely only on a previous UI lookup. In Order Service, I derive the customer identity from verified authentication rather than trusting a userId supplied in the request body.

### How do I use it? — Refresh token 轮换是什么意思

The exercise uses a fifteen-minute access token and a refresh session with an absolute seven-day limit. The server stores a hash of the refresh token and its session state. On a valid refresh, it atomically consumes the old refresh token and issues a replacement, while keeping the session's absolute deadline.

If an already-consumed refresh token is presented again, the server treats it according to the session-reuse policy and can revoke the token family. The client coordinates refresh requests so two tabs do not accidentally behave like a stolen-token replay. Logout revokes the refresh session; an existing access token can remain valid until expiry unless the system also performs online revocation checks.

### Ticket story — 卖家越权修改商品

The ticket is that two sellers share the management API and changing an item ID could target another seller's product. I compare a Gateway-only rule with a service rule that checks the actual ownership relationship. With my manager, I define the buyer, seller and support permission matrix and choose to enforce ownership in the service that owns the data.

I then test with two seller accounts and two buyer accounts. Having a valid seller token should allow management of that seller's products, not every product in the system. The browser remains useful for user experience, but it is not the security boundary.

### What if it fails, and how do I verify it?

Missing or invalid authentication normally produces 401; an authenticated caller without permission normally receives 403, or a policy-driven 404 to avoid exposing resource existence. I test changed signatures, expired tokens, wrong issuer or audience, cross-owner access, and direct service requests. Tests that mock an authenticated user cover authorization but do not replace tests through the real JWT decoder.

For key rotation, trusted old public keys remain available for the intended overlap window while new tokens use the new signing key. For browser storage, I account for XSS and CSRF rather than claiming a token makes both disappear. Refresh cookies need appropriate cookie attributes and CSRF protection; CORS is a browser policy, not authorization.

这章的主线是“你是谁、你能做什么、你是否拥有这个对象”。JWT 本身不会替你检查 ownership，也不代表 Auth 与 Account 两张表必须同步复制密码。资源服务器机制见 [Spring Security JWT 文档](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)。

<a id="threads"></a>
## 11. Multithreading：Thread Pool、CompletableFuture 与下单

### What is it? — 先解释线程和线程池

Multithreading allows several tasks to make progress during the same period. On multiple CPU cores, some tasks can run at the same time; for network calls, a major benefit is that one task can wait for a response while another makes progress. It does not automatically make every piece of code faster.

A thread pool reuses a controlled number of worker threads instead of creating a new thread for every task. Tasks wait in a queue when workers are busy. A bounded queue and a rejection policy make overload visible instead of allowing waiting work to grow until the application runs out of memory.

CompletableFuture represents a result that will become available later and lets me combine dependent or independent work. It is not itself a thread pool. When I submit blocking product lookups, I explicitly choose the executor instead of relying on the shared default pool.

### Why do I use it? — 下单的哪一段适合并发

In checkout, product-detail reads for different items can be independent. If the application waits for each response before starting the next, a cart with several products accumulates network waiting time. Overlapping those reads can shorten that stage.

Inventory deductions and payment requests are different. They change business state, and partial success creates recovery obligations. I keep this ticket limited to independent reads and do not turn the entire order operation into an in-memory background task.

### How do I use it? — 明确线程、队列和等待边界

Order's orchestration code validates the cart and submits product lookups to a dedicated executor. It collects every required result before beginning the reservation workflow. If a required lookup fails or the executor rejects work, the request does not proceed into stock mutation.

The example has four Order instances, eight fixed lookup workers per instance, a queue capacity of thirty-two, and at most eight product lines per request. Each downstream request has a configured network timeout, while the lookup stage has an overall deadline that includes queue time. These settings are a load-test starting point, not universal defaults.

I keep database transactions outside the waiting period. I also propagate and clear trace context, use immutable request data, and do not share a JPA EntityManager across workers. The HTTP connection pool needs a compatible capacity limit; having eight workers does not help if they all wait indefinitely for one connection.

### Ticket story — 为什么没直接选 Batch API

The ticket is slow checkout validation for multi-item carts. I compare a batch product endpoint, parallel reads and more caching. A batch endpoint can reduce call count and repeated backend work, while parallel reads preserve the current API but increase simultaneous load.

I discuss the scope with my manager and the catalog owner. We choose bounded parallel reads as the smaller first change and reserve a batch contract for separate work. We agree on downstream capacity, the maximum cart size, and controlled failure instead of unlimited waiting.

### What if it fails? — 队列、ForkJoin 与超时追问

A BlockingQueue supports operations that wait when appropriate, but ThreadPoolExecutor normally tries to enqueue submitted tasks without making every submitter wait for free queue space. Its worker, queue and maximum-size rules decide when it expands or rejects. An unbounded queue can prevent maximumPoolSize from providing the expansion people expect.

ForkJoinPool uses work stealing and is useful for suitable divide-and-conquer tasks. I do not choose a shared common pool for these blocking HTTP lookups because unrelated work would share its capacity. Similarly, parallelStream does not give this checkout path a clear dedicated downstream budget.

When the queue is full, explicit rejection produces a controlled overload response. CallerRunsPolicy would execute work on the submitting thread and change that budget; it is especially dangerous if the caller is a Gateway event-loop thread. If some submissions succeeded before another is rejected, I cancel or collect the existing tasks and still prevent the reservation stage.

CompletableFuture timeout or cancellation does not guarantee that the underlying HTTP operation stopped. I configure the client's network timeouts as well. A timed-out read might continue briefly; that is manageable here because it is a read, not an untracked stock deduction.

### Result and numbers — 延迟、吞吐、并发不是一个数

Four independent calls taking about eighty milliseconds each require roughly 320 milliseconds when performed serially. With available capacity, overlapping them can move that stage toward the slowest call plus overhead. It still makes four calls; only batching changes four HTTP calls into one.

At fifty orders per second and four items per order, the lookup rate is 200 calls per second. With an average duration of 0.08 seconds, average in-flight work is approximately sixteen calls across the service. If average duration increases to 0.5 seconds, the same traffic needs approximately one hundred in-flight calls, so our thirty-two total workers intentionally queue or reject pressure rather than expanding without limit.

我会比较同一负载下的 checkout p95/p99、排队时间、拒绝率和 Item Service 延迟，而不是把 320ms 到 80ms 的理论等待差写成整条下单实测提升。线程池机制可查 [Java 17 ThreadPoolExecutor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html)。

<a id="practice"></a>
## 12. 模拟追问与练习方法

每个主题先练一段约一到两分钟的回答，包含定义、业务用途和基本实现；对方继续追问时，再展开 ticket 故事与异常场景。开放问题先解释技术，事故问题可以直接解释用户受到的影响；两种回答不是固定背诵同一个开头。事务至少要讲清一起提交哪些记录，Kafka 至少要讲清谁发送、谁保存和谁消费。

| 面试官追问 | 应转到的关键点 |
|---|---|
| 为什么不直接加 consumer group | 原 group 内增加成员才分摊；新 group 是独立订阅 |
| consumer 扩容后为什么仍然慢 | 单分区热点、下游容量、重平衡、提交统计滞后 |
| acks 调成 1 不就快了吗 | 性能与丢失窗口的取舍，交易事件不能只追吞吐 |
| manual commit 是不是 exactly once | PostgreSQL 与 offset 之间有窗口，靠业务幂等 |
| DLT 发布也失败呢 | 不当作恢复成功，不跳过源进度，告警并重试 |
| 为什么用了事务还出现部分成功 | 是本地多表、跨线程还是远程调用，先定位原子边界 |
| MANDATORY 为什么不是 REQUIRED | 希望错误调用直接失败，而不是悄悄独立建事务 |
| REQUIRES_NEW 能解决所有异常吗 | 破坏共同回滚，额外连接与锁，异常仍可传播 |
| 支付超时就取消并释放吗 | 结果未知，要核对原支付；迟到成功需要恢复或退款 |
| JWT 能保证 seller 只能改自己的商品吗 | 角色检查加可信身份与持久化归属检查 |
| 线程池满了怎么办 | 明确拒绝/短等待预算，不能静默丢弃或无限扩大 |
| 你的结果到底是什么 | 说明业务验收、测量条件、计算与实测的区别 |

最后用三个问题检查每个故事：如果我不做这张 ticket，用户会遇到什么？另一个方案为什么没有选？系统在最不方便的时刻宕机会怎样恢复？能连贯回答这三个问题，比多背几个参数更能体现 senior 的判断力。

<a id="resume-review"></a>
## 13. 简历建议与需要统一的口径

这次比较基于 `.doc` 正文提取，没有修改简历原文件，也没有做版面渲染。主要问题不是缺技术，而是 Summary 和经历中重复技术较多，最新项目职责很广却缺少清楚的主次；原面试稿又加入了简历没有交代的团队、规模及工具选择。

### 先处理经历身份与时间线

简历列出的工作时间从 May 2018 至今，Summary 写 7+ years。7+ 并非必然错误，但若截至 2026 年这些都是连续、可计入的专业经历，可以核实是否应更新为 8+；不能只按日期相减，把实习、兼职或重叠经历重复计入。本文先保留简历原有 7+，不替你决定。

本科时间 2017–2021，与 Alipay 2018–2021 经历重叠；硕士 2023–2025 与美国工作时间重叠。这不自动意味着问题，但应该能清楚说明实际工作的性质、地点及学习安排；不要为避免追问而编造实习、远程或兼职身份。相邻岗位都以 Jan 结束/开始时，可准备准确交接日期。

你此前说明自己在咨询公司。若 Wolf & Badger 等是客户而不是发薪雇主，建议使用“Consulting employer / Client engagement”结构，注明真实雇主、客户、角色和日期；New York 是客户地点还是工作地点也应一致。当前文档沿用“project for Wolf & Badger”，不擅自把客户写成直接雇主；如果客户名称受保密限制，再按实际政策处理。

### 缩短 Summary 并明确最新项目主线

Summary 目前接近两打 bullet，把 ORM、SQL、NoSQL、Kafka、容器、AI 等重新讲了一遍；这些在技能表和经历又出现。建议将它压缩为一段或四条左右，保留经验领域、最强业务职责、分布式可靠性和相关 AI 能力。愿意搬迁可放联系方式附近，不需要占专业总结的一条。

最新项目十四条职责可以保留在咨询详细版，但招聘阅读版可压成约九至十条。账户/服务通信作为背景，订单、库存、支付和线程池作为主要贡献，搜索与 AI 作为次要亮点；AWS 与 Docker/Kubernetes 可合并，通用数据映射与相应业务条目合并。不要把库存防超卖与支付防重简单合成模糊的“提高可靠性”，它们有不同问题和实现边界。

建议顺序是业务 ownership、订单生命周期、库存/支付可靠性、Kafka 恢复、并行详情读取、缓存/搜索、安全、云发布、测试、AI。若投递 AI backend 岗位，可将 AI 提前；如果投递交易/电商岗位，不必让它抢走 checkout 的主线。团队范围宽可以真实存在，但面试要能区分主责、参与集成和平台团队提供的能力。

### 用准确表述替换容易被追问的词

| 简历中的位置 | 容易产生的问题 | 建议处理 |
|---|---|---|
| NoSQL 总结的 schema-less | 把 Cassandra 和 MongoDB 的建模特点混在一起 | 改成 query-oriented modeling / flexible document schemas，按数据库区分 |
| Redis L2 的 cross-node consistency | 共享缓存不是自动强一致 | 说明 shared cache access 以及 expiration/invalidation，不暗示一致性保证 |
| HyperLogLog / Bitmap 同时接 ranking、counters | 各数据结构职责不清 | HLL 讲近似 UV，bitmap 讲标记/精确位计数；ranking/counters 单独说明实际结构 |
| Java 新特性提高 performance | Lambda/Stream 本身不保证更快 | 若没有对比结果，强调 readability，性能落到具体查询/并发优化 |
| CI/CD 中 CD 固定写 Continuous Deployment | 有生产审批时未必全自动 | 按实际流程写 delivery 或 deployment |
| Log4J 与旧 Q&A 的 Logback | 两个实现不能当同一套配置 | 确认实际是否 Log4j 2；SLF4J 是门面，Splunk 是采集查询系统 |
| AWS ECS、Kubernetes 与 Q&A 的 EKS | 云服务分属不同部署方案 | 按项目说明；简历未确认 EKS，不自动把 Kubernetes 等同于 EKS |

技能分组也可更准确：React 属于框架/库，不是编程语言；HTML/CSS 是 Web 技术；REST 与 Microservices 是接口/架构概念。Postman 重复，Milvus 在最新职责中有但技能栏缺失；Bedrock 大小写、JUnit、JUnit 5、Log4j 2 的名称应统一，前提是实际使用对应版本。银行经历有 `components.•` 的多余符号，`Responsibility` 可统一为 `Responsibilities`。

### 哪些数字值得补，哪些先不要写

优先补能解释的接口或业务量、指定环境下的 p95/p99、调用数量、数据规模、故障恢复时间与测试场景。当前简历并没有 1M DAU、200 QPS、500 Kafka events/s 或“性能提升 75%”，这些仍留作架构计算，不因面试稿里出现就写成客户线上数据。每个数字应能回答范围、时间窗口、基线及测量方式。

可以用“覆盖 100 个并发购买请求且库存不为负”表达已执行的验收，也可以用“将每行查询改成批量读取”表达可核对的实现变化，但尚未执行的测试仍不能说已经通过。比起给每条 bullet 都加百分比，给两三个主要贡献准备有依据的结果更有用。

<a id="resume-map"></a>
## 14. 最新项目十四条职责对照

下表按简历 Wolf & Badger 部分的原顺序编号，帮助你被点到任意一条时都能继续回答。正文故事提供演练情境，职责范围来自简历；不把 Alipay 的结算或 TikTok 的互动日志自动搬进最新项目。

| 简历职责 | 对应准备内容 | 额外应确认的事实 |
|---|---|---|
| 1. Java 17、REST、Feign、Eureka、Gateway | 介绍；V2 第 6、7、21 章 | 服务边界及谁拥有接口，Kubernetes 与 Eureka 的分工 |
| 2. 订单创建到退款 | 第 9 节 Saga；V2 第 26 章 | 取消、发货与退款竞争规则 |
| 3. 库存预留、释放与防重复 | 第 7、9 节；V2 第 27 章 | 原子更新、预留唯一身份、重复释放 |
| 4. WebClient/Resilience4j 支付退款 | 第 9 节；V2 第 31 章 | 提供方幂等与查询契约、未知结果 |
| 5. Kafka 事件与失败恢复 | 第 3–6 节 | group、key、ack/offset 和 DLT owner |
| 6. PostgreSQL 权威数据与 Cassandra 商品视图 | 第 7、8 节；V2 第 9、13 章 | 简历只主张商品视图，不自动主张最新项目拥有所有行为/消息表 |
| 7. Redis 商品、卖家和订单缓存 | 第 15 节 | TTL、key 范围、失效与实际调试过程 |
| 8. Elasticsearch 关键词/价格/分类 | 第 16 节 | mapping、事件同步、排序、删除与新鲜度 |
| 9. CompletableFuture 与有界线程池 | 第 11 节及介绍主 ticket | 实际线程/队列/超时和测量结果 |
| 10. Spring Security/JWT 与 session renewal | 第 10 节；V2 第 40 章 | 刷新机制、角色转换、ownership；不能只会讲 token 格式 |
| 11. AWS 网络、权限和存储 | 第 18 节 | 具体服务、环境权限、自己和平台团队的职责 |
| 12. Docker/Kubernetes 发布 | 第 18 节 | 真实平台、探针、资源、发布与回滚 |
| 13. JUnit 5/Mockito/Testcontainers | 各故事 Result；V2 第 22、34 章 | 哪些故障测试实际执行、真实组件还是 mock |
| 14. AI、Milvus、分块与工具 | 第 17 节 | 模型、维度、collection、工具边界、评测与个人职责 |

<a id="cache-story"></a>
## 15. Redis Cache：是什么、缓存什么、怎样保证可用

### What is it? — 英文开场

Redis is an in-memory data store that I use here as a shared cache. A cache keeps frequently requested data closer to the application so repeated reads do not always reach the main database. In this project, Redis is not the final authority for payment amounts or available stock.

Cache Aside means the application checks the cache first. On a miss, it loads the data from the source, returns the result and stores a cached copy for later requests. A miss is therefore a normal part of the design, not automatically a bug.

### Why do I use it? — 缓存哪些东西，为什么

Product details and seller listing pages are often read more frequently than they change. Caching those results reduces repeated reads and can improve response time. For order information, I am more selective because status changes and ownership checks make correctness more sensitive.

I do not cache every query without limits. Large unbounded lists consume memory and are expensive to invalidate. I cache bounded pages or product-level entries and include the relevant seller, page, sort and filter parameters in the key.

### How do I use it? — 命中、失效和热点处理

For a product read, the service checks a key such as product:v5:123. If it is absent, the service loads the product and stores a compatible serialized value with a TTL. In this exercise, product details use a five-minute TTL with random variation, while seller listing pages use a shorter thirty-second TTL.

When a product changes, the source-of-truth transaction commits first and the cache invalidation follows. A durable product event provides a retryable invalidation path. TTL limits how long an entry can live, but it does not by itself prevent a racing old read from repopulating stale data; strict freshness needs stronger version/fencing rules or an authoritative read.

Negative caching means briefly caching a genuine “not found” result, for example for ten seconds, so repeated invalid IDs do not repeatedly hit the database. I do not turn database timeouts into cached “not found” results. Request coalescing means that simultaneous misses for the same key share one load rather than each starting another query; a process-local mechanism only merges callers on that process.

### Ticket story — 为什么缓存命中反而报错

The debugging story is that a product loads on a cache miss but fails on a later request that reads the cached value. I check the value representation and serializer before assuming that Redis contains no data. An old serialized object can be incompatible with a changed Java type even when the database record is healthy.

With my manager, I compare bypassing the affected cache with changing the serializer and switching to a new cache namespace. Bypassing helps restore service but increases source load. I choose a compatible representation, introduce a versioned namespace when necessary, and test both old-data handling and new writes.

### What if it fails? — Redis 宕机和过期风暴

If Redis fails, the application may fall back to the source, but I limit that traffic instead of sending every failed cache read to PostgreSQL at once. Short Redis timeouts, rate limits and controlled overload responses protect the database. Stale browsing data may be acceptable under a specific policy; stale payment or stock decisions are not.

I use varied expiry times to reduce synchronized misses and keep keys scoped correctly. Seller data must include seller identity, and a cached order does not bypass authorization. A key named SimpleKey [] indicates a no-argument method key, not an empty cached value.

### Result and numbers — 怎样证明缓存工作

At 500 eligible product reads per second and a 90% hit rate, the simplified source-read rate is fifty per second rather than 500. That assumes each miss loads once and excludes refresh work, retries and stampedes. I verify the real behavior using hit/miss metrics, source query counts, latency and correct returned data.

缓存修复至少要测试第一次 miss、第二次 hit、非空列表、商品更新后读取和 Redis 不可用。页面能显示 JSON 只证明某条路径成功，不能证明 Redis 命中正常，更不能证明缓存内容与数据库永远一致。

<a id="search-story"></a>
## 16. Elasticsearch：关键词搜索为什么单独建索引

### What is it? — 英文开场

Elasticsearch is a search engine that stores searchable documents and builds indexes for finding them. An inverted index connects words to the documents containing them, which supports text queries without checking every description one by one. It also supports filtering and relevance scoring.

In the shopping project, a document represents a product. Product names and descriptions are searchable text, while category, currency and other exact attributes use appropriate filter fields. A search index is a derived view, not the source of truth for completing a purchase.

### Why do I use it? — 和数据库按 ID 查有什么不同

Looking up a known item ID is different from asking for “waterproof travel bag under a certain price.” Customers need keyword matching, filtering and useful ranking. Those query needs motivate a search index; the presence of many products alone does not automatically require Elasticsearch.

PostgreSQL full-text search is an alternative with fewer components. Elasticsearch offers dedicated search capabilities and separate scaling, but it introduces index maintenance, operational cost and a delay before changes become searchable.

### How do I use it? — 从商品事件到搜索结果

Product events update documents using itemId as the stable document ID. Updates carry a version so an older event cannot overwrite a newer document, and deletion or unavailability is represented deliberately. Name and description are analyzed text; category and currency support exact matching; prices use an explicit numeric and currency model.

The endpoint bounds page size and combines a text query with structured filters. I avoid issuing a separate remote call for every search result by using bounded batch hydration when current display data is required. Checkout independently validates the authoritative price and inventory.

For large result sets, I use an appropriate cursor-style pagination approach rather than arbitrary deep offsets. Changing a mapping substantially normally requires creating a compatible new index, rebuilding it and switching an alias after validation.

### Ticket story — 搜索需求如何变成选型

The ticket is that buyers cannot narrow results effectively with simple product-name matching. I compare improving database search with introducing a dedicated index and discuss query requirements, relevance and operating cost with my manager. We accept a defined browsing freshness window but keep checkout validation independent.

I create a fixed set of shopping queries and expected relevant products. That allows us to judge the search change by useful results instead of only whether the endpoint returns HTTP 200.

### What if it fails? — 索引落后和数据删除

If Elasticsearch is unavailable, committed product changes remain recoverable in the event pipeline. Search can report temporary unavailability or provide a deliberately limited alternative; I do not replace a failed index with an unlimited database scan. When it recovers, event replay or a rebuild restores the view.

An old event arriving after a delete must not resurrect a removed listing. Version and deletion policies must also hold through replay and index rebuilds. The same applies when a new and old index run together during migration.

### Result and numbers — 延迟与容量怎样讲

For an exercise with one million products and an average two-kilobyte source document, the source JSON is about two gigabytes before index structures, replicas and operating headroom. That is not the actual disk size, and shard count should come from measured indexing and search load rather than the product count alone.

验收同时看查询相关性、更新新鲜度、p95/p99 和删除正确性。Elasticsearch 是关键词和结构化条件搜索，Milvus 是语义相似候选检索，二者不能只因为“都能搜”就当成同一种组件。

<a id="ai-story"></a>
## 17. AI Shopping Assistant：Embedding、Milvus 与 Agent

### What is it? — 先把几个名字讲成人话

An embedding represents text as a list of numbers so that a retrieval system can compare semantic similarity. Milvus is a vector database that stores those representations and searches for nearby vectors. This helps when a customer's wording differs from the exact words in a product title.

An agent is an application pattern in which a language model can choose from controlled tools while answering a request. In this shopping assistant, the tools search the catalog and fetch product details. The model helps interpret the request and explain results; it does not become the authority for price, availability or permissions.

### Why do I use it? — 解决哪种购物需求

A customer may ask for a lightweight waterproof bag for a weekend trip, then add a budget or color preference. Keyword search alone may miss relevant descriptions, and a general language model may invent products or facts. Retrieval connects the conversation to actual catalog items.

I use the assistant to support product discovery and follow-up questions. I do not describe content-based semantic retrieval as personalized recommendations unless the design actually uses relevant user preferences or history with appropriate consent and evaluation.

### How do I use it? — 商品如何进入向量库

The ingestion process prepares product text and splits long descriptions into useful chunks when needed. Each vector record includes itemId, chunkId, source version and searchable metadata. Short descriptions do not need arbitrary splitting just to claim that chunking is implemented.

At query time, the application uses a compatible embedding model, searches for candidates, combines chunks belonging to the same product, and checks exact constraints through catalog tools. A similar description does not prove that an item meets the price limit or remains in stock. Follow-up messages update the customer's constraints without treating model output as trusted instructions.

Changing the embedding model can require rebuilding the vectors because the new representations may not be compatible. Product updates and deletions must also remove or replace stale chunks instead of leaving old descriptions available forever.

### Ticket story — 怎么和经理限制范围

The ticket is helping shoppers who know what they need but not the product name. I compare keyword search, semantic retrieval and a tool-using assistant. With my manager, I define a discovery-only scope: recommend real products and explain them, without placing orders or charging customers.

I then choose tools with strict input schemas and a limited call budget. The backend validates permission and arguments regardless of what the model requests, and retrieved product text is treated as data rather than trusted instructions.

### What if it fails? — 模型和检索的故障

If retrieval returns no reliable candidates, the assistant asks a clarifying question or says that it could not find a match. If current product details cannot be retrieved, it does not invent a price. Timeouts and tool failures have a clear user-facing outcome rather than an endless chain of model calls.

I also test malicious instructions embedded in product descriptions, invalid tool parameters, unavailable products and conflicting follow-up requests. Recommending an item that violates an explicit budget is a failure even if the prose sounds convincing.

### Result and numbers — 怎样评估，不编造转化率

A useful exercise is a test set of one hundred shopping questions, including multi-turn requests and unavailable items. I measure whether recommendations reference real products, satisfy explicit filters, and are useful according to a defined evaluation rubric. I also track response latency, tool calls and model cost.

如果做容量练习，一百万商品、平均三个 chunk、768 维 float32 向量，原始向量约 9.216 GB；它不包含索引、metadata、复制和内存开销。这组假设不能替代实际模型、维度和 collection 配置，也不是销售转化提升的证据。

<a id="deployment-story"></a>
## 18. AWS、Docker、Kubernetes 与 CI/CD：怎样发布服务

### What is it? — 每个组件是什么

Docker packages the application and its runtime dependencies into an image. Kubernetes runs and manages containers across machines, maintaining the requested replicas and coordinating replacements. AWS supplies cloud resources; the exact AWS services depend on the deployment.

CI/CD is the delivery process around those components. Continuous integration builds and tests changes, while continuous delivery prepares a repeatable release that may still require approval. Continuous deployment goes further by automatically releasing qualifying changes to production.

### Why do I use it? — 为什么不是把 Jar 复制上去

The shopping project has several services with different configuration and dependency needs. Repeatable images and deployment settings reduce differences between environments. Controlled releases also matter because active orders and Kafka consumers cannot simply forget unfinished work when a process is replaced.

Kubernetes does not make the application correct by itself. The order workflow still needs persisted progress, safe retries and compatible interfaces so that old and new versions can run together during a release.

### How do I use it? — 从提交到上线

A code change goes through review, automated tests and an image build. The pipeline tags the image with a traceable version or digest, runs the relevant integration checks, and promotes the same artifact through environments. Configuration and secrets are supplied separately rather than baked into the image.

For this deployment exercise, Kubernetes runs on AWS EKS and images are stored in ECR. That is a concrete architecture choice for practice, not something to infer merely from the resume saying “AWS and Kubernetes.” Service accounts and cloud permissions follow least privilege, and persistent data has a separate storage and recovery plan.

Database changes follow an expand-and-contract sequence. I first add compatible schema support, then deploy code that can work during the transition, migrate existing data if needed, and remove obsolete structures only after old versions no longer depend on them. Application rollback is not the same as reversing a destructive database migration.

### How do I use it? — 三种 Health Probe 有什么区别

Readiness asks whether a Pod is ready to receive normal service traffic. Liveness asks whether the container needs restarting because it cannot recover on its own. Startup probes allow a slow-starting application to initialize before the other probes begin their usual checks.

I avoid making a brief shared database outage trigger liveness failures on every application Pod. Restarting all services does not repair PostgreSQL and may increase recovery pressure. Readiness and degraded behavior need to reflect which requests the service can safely handle.

### Ticket story — 滚动发布期间还有订单怎么办

The ticket is releasing an Order Service change while buyers are checking out. I compare rolling updates with blue-green deployment and discuss capacity, rollback and schema compatibility with my manager. The exercise uses a rolling update because the change is backward compatible and spare capacity is available.

I configure graceful shutdown so incoming traffic drains and consumers do not acknowledge unfinished work. Then I check both technical health and business signals after release: successful order acceptance, pending workflow age, payment errors and Kafka processing.

### What if it fails, and how do I verify it?

For an example deployment with four replicas, maxSurge=1 and maxUnavailable=0 permit one extra Pod while keeping the desired available count during the controlled rollout. This still requires sufficient cluster resources, correct readiness and safe shutdown; the settings alone do not promise zero failed requests.

If the new version fails readiness, the rollout should not continue normally. If business errors rise after traffic reaches it, I pause or revert to a compatible image and inspect unfinished work. I never describe rollback as undoing an already completed payment.

部署故事要明确自己负责哪些 pipeline、manifest、应用关闭逻辑，哪些由平台团队维护。Jenkins、GitHub Actions、EKS 都不能因为文档举过例子就变成已确认履历；探针语义可查 [Kubernetes probes](https://kubernetes.io/docs/concepts/workloads/pods/probes/)。

<a id="career-boundaries"></a>
## 19. 不同项目的经历如何区分

| 简历项目 | 文本明确强调的内容 | 不应自动转移到最新项目 |
|---|---|---|
| Wolf & Badger，Jan 2025–Present | 订单/库存/付款、商品搜索、Redis、Milvus、AWS/Kubernetes、线程池 | 未写团队名、用户量、具体 CI 工具、模型或云集群服务 |
| TikTok，Jan 2023–Jan 2025 | User Engagement Platform、Caffeine/Redis、行为事件、WebFlux、gRPC、Jenkins | 不把购物订单当 likes/follows；不把 L1 缓存当最新项目已确认配置 |
| Bank of China，May 2021–Jan 2023 | 交易平台现代化、ExecutorService、JPA、Security、Actuator、Jenkins | 金融合规、云环境与个人权限要按真实项目说明 |
| Alipay，May 2018–Apr 2021 | Transaction Core Services、gRPC、支付授权/捕获、JDBC 对账、Kafka | 不把早期支付平台全部职责说成最近一年独立完成 |

面试官问 “Have you used Jenkins?” 可以明确选 TikTok 或银行经历讲完整流程，而不是强行换掉最新项目工具。问 “Have you used gRPC?” 可以使用相关旧项目，再解释最新购物服务为什么采用 HTTP/OpenFeign；这能显示选型取舍，而不是所有项目都使用所有技术。

日志口径也要统一。简历写 Log4J，V2 的完整方案举例 Logback，两者都是日志实现而不是 Splunk 的别名；先确认是否为 Log4j 2，以及哪个项目使用。可以解释应用通过日志门面输出结构化记录，采集器送入集中平台，但不能据此认定简历已经声明使用 Splunk；若面试要主张 Splunk 经验，准备实际字段、查询、采集配置和排查实例。

正式练习前还需要填写团队名称、consulting employer/client 关系、最新流水线工具、日志实现与平台、真实下游支付契约，以及线程池 ticket 的实际配置和测试结果。没有这些事实时，保留方案讨论而不是补造公司内部信息。

## 官方资料与版本说明

以上参数、示意代码和故事是架构练习，不是可直接复制的完整 Spring 配置。链接可能指向较新的框架文档，落地时需要匹配项目实际依赖版本，特别是 listener 错误恢复、异步提交及事务管理器支持范围。

- [Kafka producer 配置](https://kafka.apache.org/41/configuration/producer-configs/)
- [Spring Kafka listener 与 offset](https://docs.spring.io/spring-kafka/reference/kafka/receiving-messages/message-listener-container.html)
- [Spring Kafka 异常与 dead-letter](https://docs.spring.io/spring-kafka/reference/kafka/annotation-error-handling.html)
- [Spring propagation 枚举](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/annotation/Propagation.html)
- [Spring 事务传播与资源边界](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html)
- [Spring Security JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
