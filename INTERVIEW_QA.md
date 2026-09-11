# Senior Java Interview Q&A — Production Shopping Platform

This document is organized as a set of interview “pages,” following the structure of the supplied examples. Each page contains a concise answer, a project-specific deep dive, numbers, and likely follow-up questions.

## Evidence rule before using this document

The candidate supplied two business statements: the platform was launched and has **1 million daily active users**. The repository cannot independently prove production traffic, cloud topology, or business ownership. Use the “launched” wording below only if company dashboards, deployment records, or your real experience support it. Otherwise use the design wording.

**Production wording — use only when verifiable:**

> The shopping platform serves approximately one million daily active users. It averages about 200 API requests per second and is designed for roughly 1,000 QPS during peak periods.

**Portfolio-safe wording:**

> I designed the shopping platform around a capacity model of one million daily active users, approximately 200 average QPS, and a 1,000-QPS peak target.

The technical answers distinguish three kinds of numbers:

- **Repository fact:** directly visible in source code, configuration, or stored test reports.
- **Derived number:** calculated from a code path or declared workload.
- **Capacity assumption:** a realistic input that still requires a benchmark.

Never describe a capacity assumption as a measured result. A senior interviewer will ask for the test environment, duration, dataset, percentile latency, error rate, and bottleneck.

---

# Page 1 — System Design

## What I say

The platform is a multi-service commerce system responsible for identity, seller catalog management, inventory, cart, checkout, payment, search, notifications, direct messages, and recommendations. External requests enter through a reactive API Gateway. Services use synchronous REST when an immediate result is required and Kafka when work can be processed asynchronously.

PostgreSQL is the system of record for users, items, inventory, orders, payments, and transactional outbox rows. Cassandra stores denormalized item read models. Redis provides shared L2 caching, idempotency acceleration, rate limiting, and approximate DAU counters. A proposed Caffeine L1 cache keeps the hottest item summaries inside each service instance. Kafka connects item projection, order, payment, notification, search, and messaging workflows.

The core consistency rule is simple: PostgreSQL decides whether business data is committed; Cassandra and Redis are rebuildable read paths. Checkout never trusts cached inventory. Item and outbox rows commit in one local transaction, and idempotent Kafka consumers update downstream views.

## High-level architecture

```text
Web / Mobile Clients
        │
        ▼
CDN / Load Balancer
        │
        ▼
API Gateway — WebFlux, JWT, routing, rate limits
        │
        ├────────────── synchronous REST / OpenFeign ──────────────┐
        │                                                          │
        ▼                                                          ▼
Auth ──► Account       Item Catalog ──► Inventory       Cart ──► Order
                                           ▲                         │
                                           │                         ▼
                                      PostgreSQL                 Payment
                                           │                         │
                                           └──── order Saga ─────────┘

Service PostgreSQL ── transactional outbox ──► Kafka
                                                   │
                 ┌─────────────────────────────────┼────────────────────┐
                 ▼                                 ▼                    ▼
          Cassandra projections                Search             Notification
                 │                                                     │
                 ▼                                                     ▼
          Caffeine L1 / Redis L2                                Email / SMS / Push

Additional consumers: Messaging, Recommendation, Analytics, Reconciliation
```

## Service boundaries

| Service | Responsibility | Primary storage | Main communication |
|---|---|---|---|
| API Gateway | Edge routing, token validation, rate limiting | None | WebFlux HTTP |
| Auth | Credentials, login, JWT issuance | PostgreSQL | REST to Account |
| Account | Canonical user ID and profile | PostgreSQL | REST and account events |
| Item Catalog | Seller-owned product metadata | PostgreSQL + Cassandra projection | REST and Kafka |
| Inventory | Available/reserved stock | PostgreSQL | Batch reservation commands |
| Cart | Temporary buyer selections | Redis/PostgreSQL depending durability | REST |
| Order | Order state machine and immutable line snapshots | PostgreSQL | REST, Kafka Saga |
| Payment | Authorization, capture, refund, idempotency | PostgreSQL | REST to provider, Kafka |
| Search | Text/category discovery | OpenSearch-style index | Kafka consumer |
| Notification | Email, SMS, push workflows | Delivery log | Kafka consumer |
| Messaging | Buyer/seller conversations | Message store | Kafka/WebSocket |
| Recommendation | Ranked product candidates | Feature/read stores | Kafka and batch/online APIs |

The current repository contains six deployable Spring Boot modules: Gateway, Eureka, Auth, Account, Item, and Order. Inventory currently belongs to Item Service. Payment, Search, Notification, Messaging, Cart, and Recommendation are production service boundaries in this system-design narrative and must not be described as implemented in this repository unless corresponding code or workplace evidence exists.

## Deep questions

### Why not use one database for everything?

One relational database would simplify consistency, but the read and write workloads have different shapes. Orders and stock require constraints and ACID transactions. Catalog reads need predictable key-based scale, and caches need sub-millisecond shared access. The architecture uses multiple stores only after assigning one authoritative owner to every datum; otherwise polyglot persistence would increase ambiguity and failure modes.

### Is this a microservice architecture or a distributed monolith?

It becomes a distributed monolith if every request makes a long chain of synchronous calls, services share tables, or deployments must happen together. I avoid that by assigning data ownership, using events for non-critical side effects, maintaining backward-compatible contracts, and keeping synchronous checkout depth short. The current `2N` Item calls in Order Service are a known example of excessive chattiness and should become one batch call.

### What are the most important non-functional requirements?

The capacity model targets 1,000 peak API QPS, 99.9% eligible-request success, catalog cached-read p99 below 50 ms, checkout p99 below 500 ms, item projection lag p99 below 5 seconds, and no overselling. These are proposed SLOs until a production dashboard or reproducible load test verifies them.

---

# Page 2 — What Does This Project Do?

## What I say

The platform supports both buyers and sellers. Sellers register, manage their own product catalog, set prices and inventory, and receive order-related messages. Buyers authenticate, browse and search products, maintain carts, place orders, pay, view order history, and receive notifications.

The backend exposes synchronous APIs for operations that need an immediate result, such as login, item reads, and inventory reservation. It also publishes domain events so Cassandra projections, search indexes, notification delivery, analytics, and recommendation features can evolve without increasing user-facing latency.

The main business invariants are that a seller can modify only their own items, a buyer cannot purchase more stock than is available, the same checkout request cannot create two orders, the same payment request cannot charge twice, and committed business changes must eventually reach all required consumers.

## Core request paths

### Login

```text
Client → Gateway → Auth → PostgreSQL
                         └→ BCrypt verification → JWT
```

### Seller creates an item

```text
Client → Gateway → Item Service
                     └→ one PostgreSQL transaction:
                        item + inventory + outbox
                                      │
                                      ▼
                                    Kafka
                                      ├→ Cassandra projection
                                      ├→ Redis invalidation
                                      └→ Search indexing
```

### Buyer places an order

```text
Client → Gateway → Order acceptance
                     └→ PENDING order + idempotency key + outbox
                                           │
                                           ▼
                                     order command
                                           │
                                           ▼
                              worker processes one order
                                  ├→ batch inventory reserve
                                  ├→ payment authorization
                                  └→ state transition + events
```

## Deep questions

### Why return `202 Accepted` for asynchronous order processing?

`202` means the command was accepted but has not reached a final business state. The response contains `orderId` and a status URL. It prevents a mobile retry or proxy timeout from being confused with order failure, while the durable Saga continues. If product requirements demand an immediate confirmed result, the API can remain synchronous, but the total timeout and retry semantics must be explicit.

### How does the client know the final result?

The client can poll `GET /api/orders/{id}`, receive a WebSocket/server-sent update, or process a push notification. The order state machine must expose `PENDING`, `STOCK_RESERVED`, `PAYMENT_PENDING`, `CONFIRMED`, `FAILED`, and `CANCELLED` rather than presenting an ambiguous boolean.

---

# Page 3 — What Did You Do?

## Interview answer template

Edit this answer so every first-person claim matches work you can defend:

> I designed and implemented the core Java microservices for authentication, account identity, seller-owned items, inventory, orders, and API routing. I introduced PostgreSQL as the transactional source of truth, Cassandra as an item read projection, Redis caching, and Kafka-based asynchronous item events using the transactional outbox pattern.
>
> I implemented declarative service-to-service communication with OpenFeign and Eureka-based discovery. I designed the global user ID flow between Account and Auth, added JWT identity and roles, enforced seller ownership in Item Service, and protected stock with an atomic conditional database update.
>
> I identified two important scaling bottlenecks from the code. An order with `N` lines makes `2N` Item calls, and an item list with `N` rows makes `N` inventory queries. My production design replaces these with batch APIs, keyset pagination, and query-specific Cassandra projections. For an eight-line order, batching removes 87.5%–93.75% of downstream calls; for a 100-item page, batch inventory loading removes 99% of inventory-query round trips.

## Repository-backed contributions

- Six Maven modules run on Java 17 and Spring Boot 3.5.7.
- Auth calls Account with OpenFeign and reuses Account’s numeric ID as the global user ID.
- JWT contains user ID, email, seller/buyer role, issuer, audience, issue time, expiration, and token ID.
- Item ownership is stored as `user_id`; seller read/update/delete paths enforce ownership.
- Item metadata, inventory, and an outbox event commit in one PostgreSQL transaction.
- Kafka uses `itemId` as the key and an idempotent producer configuration.
- Cassandra maintains `items_by_id`; Redis caches item and order reads for ten minutes.
- Inventory reservation uses one conditional SQL update rather than read-modify-write.
- The latest stored Maven reports contain 13 tests with zero failures and errors.

## Deep questions

### What was the hardest design decision?

The hardest decision was separating authoritative inventory from high-scale catalog reads. Storing everything in Cassandra would make the diagram simpler, but stock is a contended transactional resource. I kept item and inventory truth in PostgreSQL, used an atomic decrement for correctness, and treated Cassandra and Redis as eventually consistent read accelerators.

### What would you improve next?

I would first batch the checkout calls and inventory reads because the code proves those round trips exist. Then I would add durable order idempotency and a Saga, versioned database migrations, query-specific Cassandra tables, multi-broker Kafka, integration tests with real infrastructure, and reproducible load tests. This answer shows prioritization without pretending that all production work is already present.

---

# Page 4 — Numbers and Capacity Model

## Traffic

The supplied scale is one million DAU and 200 average QPS. That implies:

```text
200 requests/s × 86,400 seconds = 17,280,000 requests/day
17,280,000 / 1,000,000 DAU = 17.28 requests/user/day
```

Using a 5× peak factor produces a 1,000-QPS peak target.

| Traffic class | Peak share | Peak QPS |
|---|---:|---:|
| Catalog and search | 70% | 700 |
| Auth and account | 10% | 100 |
| Cart | 10% | 100 |
| Order and payment | 5% | 50 |
| Messaging, notification, other | 5% | 50 |

## Business volume assumptions

| Entity | Capacity input | Annual implication |
|---|---:|---:|
| Registered accounts | 5 million retained | Dataset for account/index testing |
| Daily active users | 1 million | Business scale supplied by candidate |
| Active/listed items | 10 million | Catalog projection test dataset |
| Orders | 80,000/day | 29.2 million/year |
| Average order lines | 4/order | 116.8 million lines/year |
| Peak order intake | 50/s | Promotion and flash-sale target |

Eighty thousand daily orders represent an assumed 8% daily conversion from one million DAU. Peak QPS is not sustained all day, so `50 × 86,400` must not be used as normal daily order volume.

## Storage model

These values are sizing examples; serialization and indexes must be measured.

| Data | Assumed effective row/event size | Logical volume |
|---|---:|---:|
| 10 million Cassandra item projections | 1 KB | ~10 GB before replication/overhead |
| 29.2 million order headers | 500 bytes | ~14.6 GB before indexes/overhead |
| 116.8 million order lines | 300 bytes | ~35 GB before indexes/overhead |
| 1 million Redis hot item objects | 2 KB | ~2 GB before Redis overhead |
| One Kafka item event | 2 KB | Rate-dependent retention storage |

With Cassandra replication factor 3, a 10 GB logical projection requires at least 30 GB before compaction and operational headroom. With Kafka ingress of 6.6 MB/s at the modeled simultaneous topic peaks, RF=3 produces roughly 19.8 MB/s of replica writes before protocol and filesystem overhead.

## SLO and error budget

An illustrative 99.9% monthly SLO permits:

```text
30 days × 24 hours × 60 minutes = 43,200 minutes
43,200 × 0.001 = 43.2 minutes/month
```

This is an example error budget, not a verified production commitment.

## Deep questions

### Why is average QPS insufficient?

Average QPS hides bursts, hot keys, background jobs, regional skew, and p99 latency. Capacity tests must reproduce peak traffic shape, read/write mix, payload distribution, cache warmth, and dependency latency. A service can pass 1,000 QPS for one minute and still fail a two-hour promotion because of memory growth, connection leaks, compaction, or Kafka backlog.

### What does a credible benchmark report include?

It identifies the Git commit, infrastructure, replica count, CPU/memory, JVM settings, dataset, traffic mix, warm-up, test duration, p50/p95/p99, success rate, CPU, allocation, GC, thread/queue state, database wait, Redis hit rate, and Kafka lag. Without those fields, a QPS number is marketing rather than engineering evidence.

---

# Page 5 — PostgreSQL and Cassandra

## What I say

PostgreSQL is the write-side source of truth because accounts, inventory, orders, payments, and outbox rows need transactions, unique constraints, and predictable concurrency control. Cassandra is a denormalized read store for item views that are accessed by known partition keys. Redis sits in front of read stores for the hottest objects.

I did not use Cassandra to replace PostgreSQL. I used it to isolate a high-volume read model from transactional writes. Kafka events propagate committed item changes, and reconciliation can rebuild Cassandra from PostgreSQL/event history.

## Data ownership

| Data | Source of truth | Projection/cache |
|---|---|---|
| Account profile | PostgreSQL Account schema | Optional Redis profile cache |
| Credentials | PostgreSQL Auth schema | Never stored in Cassandra |
| Item metadata | PostgreSQL Item schema | Cassandra + Redis/Caffeine |
| Inventory | PostgreSQL Inventory table | Optional display hint only |
| Orders and lines | PostgreSQL Order schema | Redis order-detail cache |
| Payment state | PostgreSQL Payment schema | Events and reporting views |

## Deep questions

### Why not perform cross-service joins in PostgreSQL?

Cross-service joins bypass API contracts and couple deployment and schema changes. Even though local development uses one PostgreSQL instance with separate schemas, service credentials should prevent direct access to other schemas. Read models that combine domains should be built from events or a dedicated reporting pipeline.

### How do you recover Cassandra after data loss?

Because PostgreSQL is authoritative, a rebuild job reads items in bounded keyset pages and republishes versioned snapshot events, or a new consumer group replays a sufficiently retained compacted topic. The new table is populated under a versioned name and traffic moves only after count, checksum, sampling, and lag validation.

### What consistency does the user see?

The command response returns the committed PostgreSQL state. Cassandra and Redis may show an older value until the outbox relay and consumer finish. The UI can use the command response immediately and refresh the query view later. Projection lag is measured as a business freshness SLI.

---

# Page 6 — PostgreSQL Tables, Indexes, and Query Tuning

## Current core tables

| Schema | Table | Important columns/constraints |
|---|---|---|
| `account_service` | `accounts` | `id` PK, unique email, username, addresses, seller, created time |
| `auth_service` | `users` | shared `id` PK, unique email, password hash, seller role |
| `inventory_service` | `items` | UUID-string ID, immutable `user_id`, price, currency, timestamps, version |
| `inventory_service` | `inventory` | `item_id` PK, available quantity, version |
| `inventory_service` | `outbox_events` | event ID, aggregate/version, JSONB payload, status, attempts, timestamps |
| `order_service` | `orders` | ID, user ID, total, currency, state, created time |
| `order_service` | `order_items` | FK, item snapshot, quantity, price, unique `(order_id,item_id)` |

Order lines copy product name and paid price so historical orders do not change when the catalog changes.

## Index design

```sql
CREATE INDEX idx_items_seller_created
    ON items (user_id, created_at DESC, id);

CREATE INDEX idx_orders_user_created
    ON orders (user_id, created_at DESC, id);

CREATE INDEX idx_outbox_pending_created
    ON outbox_events (created_at)
    WHERE status = 'PENDING';

CREATE UNIQUE INDEX uk_orders_user_idempotency
    ON orders (user_id, idempotency_key);
```

The first two match filter plus ordering for seller items and user order history. The partial outbox index excludes the much larger published history. The idempotency index is the final duplicate-order guard.

## Keyset pagination

```sql
SELECT id, total_price, currency, status, created_at
FROM orders
WHERE user_id = :userId
  AND (created_at, id) < (:lastCreatedAt, :lastId)
ORDER BY created_at DESC, id DESC
LIMIT 50;
```

With the matching composite index, a deep page seeks to the cursor and reads roughly the next 50 candidates instead of discarding hundreds of thousands through a large offset.

## Deep questions

### How do you prove an index helps?

I compare `EXPLAIN (ANALYZE, BUFFERS)` before and after using production-shaped data. I check actual rows, loop count, index/sequence scan, sort, heap fetches, buffer hits/reads, and total time. An index that improves a selective read may still increase insert/update cost and storage. PostgreSQL documents plan analysis in its [EXPLAIN guide](https://www.postgresql.org/docs/current/using-explain.html).

### Why not index every column?

Each index consumes disk, cache, WAL, vacuum work, and write I/O. Low-cardinality columns such as order status often do not justify a standalone index. A partial index on active states or a composite index matching a real query can be more useful.

### What is the transaction isolation level?

PostgreSQL defaults to Read Committed. Each statement receives a snapshot of committed rows at statement start. Repeatable Read gives a stable transaction snapshot, while Serializable prevents serialization anomalies but may abort transactions that the application must retry. I choose isolation according to the invariant rather than globally selecting the strongest level. See [PostgreSQL transaction isolation](https://www.postgresql.org/docs/current/transaction-iso.html).

### How do you migrate 100-million-row tables?

I use Flyway/Liquibase and expand-migrate-contract. Add nullable columns first, deploy code that can read old and new representations, backfill in small key ranges, create indexes with an online/concurrent strategy where supported, validate, switch reads, and only later enforce `NOT NULL` or remove old columns. Hibernate `ddl-auto=update`, used locally here, is not a production migration system.

---

# Page 7 — Cassandra Partitioning and Load

## What I say

Cassandra is query-first. The partition key chooses the owning replicas; clustering columns sort rows within that partition. The current `items_by_id` table uses `id` as its partition key, producing one-row partitions and efficient point reads. It cannot efficiently support global listing or seller browsing, so the production model adds query-specific tables.

## Query tables

```sql
CREATE TABLE items_by_id (
    item_id text PRIMARY KEY,
    seller_id bigint,
    name text,
    price decimal,
    currency text,
    version bigint
);

CREATE TABLE items_by_seller_bucket (
    seller_id bigint,
    bucket smallint,
    created_at timestamp,
    item_id text,
    name text,
    price decimal,
    currency text,
    version bigint,
    PRIMARY KEY ((seller_id, bucket), created_at, item_id)
) WITH CLUSTERING ORDER BY (created_at DESC, item_id ASC);

CREATE TABLE items_by_category_day (
    category_id text,
    day date,
    ranking_score double,
    item_id text,
    name text,
    price decimal,
    PRIMARY KEY ((category_id, day), ranking_score, item_id)
) WITH CLUSTERING ORDER BY (ranking_score DESC, item_id ASC);
```

## Bucket calculation

For an extreme seller with one million item rows, use `hash(itemId) % 16`:

```text
1,000,000 / 16 = 62,500 rows/partition
62,500 × assumed 1 KB = about 62.5 MB/partition
```

Apache Cassandra gives a general guideline of fewer than 100,000 values and less than 100 MB per partition. The 16-bucket model stays below both estimates, but a seller-wide request may fan out to 16 partitions. Actual row size, tombstones, compaction, and p99 must be measured. See the [official Cassandra data-modeling guide](https://cassandra.apache.org/doc/stable/cassandra/developing/data-modeling/intro.html).

## Load distribution

Random UUID-like item IDs normally distribute `items_by_id` writes around the token ring. Seller and category keys can become hot, which is why bucket is part of their composite partition key. Metrics must track partition size, read/write latency, coordinator timeouts, dropped mutations, tombstones, compaction pending tasks, disk use, and repair progress.

## Deep questions

### How does Cassandra handle concurrent updates?

Cassandra uses timestamp-based last-write-wins reconciliation for ordinary mutations. That is unsuitable for deciding who gets the last inventory unit. Item projections include an aggregate version, and the consumer ignores older or equal events. Strong inventory reservation remains in PostgreSQL.

### Why not use Cassandra lightweight transactions for stock?

LWT provides compare-and-set semantics through additional coordination, but it has higher latency and lower throughput than normal Cassandra writes. The Cassandra modeling guide recommends minimizing LWT. PostgreSQL already owns inventory and supports the conditional decrement directly.

### How do consistency levels and replication interact?

The local keyspace has RF=1 and `SimpleStrategy`, suitable only for one-node development. A production regional cluster would normally use `NetworkTopologyStrategy` and RF=3. `LOCAL_QUORUM` then requires two replicas in that datacenter for reads or writes. Using quorum for every catalog read may be unnecessary if the projection tolerates bounded staleness.

### What are tombstones?

Deletes and TTL expiration produce tombstones until compaction can safely purge them. Large tombstone scans increase read amplification and tail latency. I avoid unbounded partitions, use TTL only when the lifecycle requires it, select compaction strategy by workload, and monitor tombstones rather than relying on a default forever.

### How do you handle a hot partition?

First confirm skew by partition-level metrics. Then increase hash/time buckets, separate a celebrity seller/category, cache the hottest pages, or change the query model. Adding nodes does not split one oversized Cassandra partition; changing the partition key does.

---

# Page 8 — Multi-Layer Cache: Caffeine and Redis

## What I say

The production cache design has two layers. Caffeine is an in-process L1 cache with no network hop. Redis is a shared L2 cache across service replicas. Cassandra/PostgreSQL is the origin. The repository currently implements Redis through Spring Cache; Caffeine is a proposed production enhancement and is not currently a dependency.

```text
Request
  │
  ├─ L1 Caffeine hit → return
  │
  ├─ L2 Redis hit → populate L1 → return
  │
  └─ Origin read → populate Redis and Caffeine → return
```

## L1 capacity

Assume each cached item summary is 1.5 KB after object overhead estimation, and each Item Service instance caches 20,000 hot items:

```text
20,000 × 1.5 KB = 30 MB raw object estimate
```

I would assign a 64–96 MB L1 budget per instance to allow cache structures and estimation error. Caffeine must use `maximumSize` or `maximumWeight` plus a short expiration such as 30–60 seconds. Caffeine supports size- and time-based eviction in its [official project documentation](https://github.com/ben-manes/caffeine/wiki/Eviction).

## L2 capacity

Assume Redis keeps one million hot item objects averaging 2 KB effective serialized size:

```text
1,000,000 × 2 KB = approximately 2 GB raw values
```

Keys, allocator fragmentation, metadata, replication, and headroom increase this. A starting primary budget might be 4–6 GB, but `MEMORY USAGE`, dataset sampling, and fragmentation ratio must replace the estimate.

## Performance model

At 700 catalog QPS, assume 50% L1 hits, 35% L2 hits, and 15% origin reads:

```text
L1 Caffeine: 350 QPS
L2 Redis:    245 QPS
Origin:      105 QPS
```

The cache layers remove 595 of 700 origin reads, an 85% reduction. With measured/assumed latencies of 0.05 ms, 1 ms, and 10 ms respectively:

```text
0.50×0.05 + 0.35×1 + 0.15×10 = 1.875 ms weighted access time
```

Compared with a 10 ms origin-only access, that model is an 81.25% reduction. Those latency inputs require measurement before being presented as results.

## Consistency

Writes commit PostgreSQL, then the outbox event updates Cassandra and clears Redis. Every service instance has its own Caffeine cache, so Redis deletion alone does not immediately clear all L1 entries. Options are a short L1 TTL, Kafka invalidation consumed by every instance through unique consumer groups, Redis client tracking, or versioned cache keys.

For item metadata I prefer versioned values and a 30–60-second L1 TTL. Inventory is never served as authoritative from L1/L2 at checkout.

## Deep questions

### What are penetration, stampede, and avalanche?

Penetration is repeated lookup of nonexistent IDs; use validation and short negative caching. Stampede is simultaneous loading of one hot expired key; use request coalescing, single-flight locking, or refresh-ahead. Avalanche is synchronized expiration of many keys; add TTL jitter and enforce origin backpressure.

With a 600-second Redis TTL and ±10% jitter, expirations spread from 540 to 660 seconds instead of occurring at exactly ten minutes.

### What happens if Redis fails?

At an 85% hit rate, normal origin load is 105 QPS. Losing Redis and L1 can raise it to 700 QPS, a 6.67× increase. The service should open a cache circuit breaker, cap origin concurrency, serve stale safe data where allowed, and shed load before PostgreSQL/Cassandra collapses.

### How do you prevent stale cache overwrite?

Every item value carries an aggregate version. A consumer or refresh replaces the value only when its version is newer. This prevents a delayed older event from overwriting a newer item. The TTL remains a final bound, not the primary ordering mechanism.

---

# Page 9 — Redis Idempotency and DAU/UV

## Order idempotency

The client generates an idempotency key for one logical checkout and reuses it on retry:

```http
POST /api/orders
Idempotency-Key: 3bc3de2e-83d6-4d2c-a7dd-603a363f638f
Authorization: Bearer <token>
```

The Redis key is scoped by verified user ID:

```text
idempotency:order:{userId}:{key}
```

The fast gate is atomic:

```text
SET idempotency:order:42:3bc3... PROCESSING NX EX 900
```

`NX` permits one creator and `EX 900` releases an abandoned in-flight entry after 15 minutes. Redis documents this atomic option in the [SET command](https://redis.io/docs/latest/commands/set/).

Redis is not the final guarantee. PostgreSQL stores `idempotency_key` under `UNIQUE(user_id,idempotency_key)`. If Redis restarts, evicts the key, or admits a race after expiry, the database still permits only one order. Payment Service also uses a durable unique key or the provider’s idempotency mechanism; financial correctness must not depend only on cache retention.

## Idempotency result states

| Redis value | API behavior |
|---|---|
| Missing | Attempt `SET NX`, then create order |
| `PROCESSING` | Return 202/status URL; do not create another order |
| `COMPLETED:{orderId}` | Return the original result |
| `FAILED_RETRYABLE` | Retry under controlled state transition |
| `FAILED_FINAL` | Return the recorded failure |

When releasing a processing lock, compare its random owner token in a Lua script before deletion. A delayed worker must not delete a newer worker’s lock.

## Idempotency memory estimate

At 80,000 orders/day and an assumed effective 300 bytes per Redis record:

```text
80,000 × 300 bytes = 24 MB for one day
```

With one replica that is about 48 MB before fragmentation. If the 50-QPS peak were incorrectly assumed to continue all day, it would create 4.32 million keys and about 1.3 GB raw, showing why peak and daily volume must not be confused.

## DAU/UV with HyperLogLog

For approximate daily unique users:

```text
PFADD dau:2026-09-08 <hashed-user-id>
PFCOUNT dau:2026-09-08
```

Redis HyperLogLog uses at most about 12 KB per key with a standard error of 0.81%. Keeping one daily key for 365 days uses roughly 4.38 MB of sketch payload before key and replication overhead:

```text
12 KB × 365 = 4,380 KB
```

That is dramatically smaller than storing one million exact user IDs per day. It is appropriate for dashboards, not billing or compliance counts. See the [Redis HyperLogLog documentation](https://redis.io/docs/latest/develop/data-types/probabilistic/hyperloglogs/).

## Deep questions

### What if Redis says PROCESSING but no order exists?

The service may have crashed after acquiring the key and before committing PostgreSQL. The processing TTL eventually expires, and the status endpoint can check PostgreSQL. A stronger design writes the order first under the database unique constraint and treats Redis only as an acceleration layer.

### Can Redis provide exactly-once order processing?

No. It can reduce duplicate execution, but network partitions, expiry, failover, and process crashes still exist. Exactly-once business effect comes from durable idempotency keys, state-machine transitions, and idempotent downstream commands.

---

# Page 10 — Why Kafka?

## What I say

Kafka is the durable asynchronous backbone. A topic is a named event stream; partitions provide ordering and parallelism; a key routes related events to the same partition. I use Kafka when the HTTP response should not wait for Cassandra projection, search indexing, notifications, analytics, or downstream workflow steps.

The implemented topic is `item.events.v1`, locally using six partitions and RF=1. `itemId` is the key, so all events for one item retain order. The consumer group `item-service-projection-v1` updates Cassandra and invalidates Redis.

## Why not direct service calls?

If Item Service synchronously calls Cassandra projection, Search, Notification, and Analytics, every dependency adds latency and availability risk. If each dependency were independently 99.9% available and all four were mandatory:

```text
0.999⁴ = 0.996006 ≈ 99.60% combined availability
```

Kafka lets Item Service commit PostgreSQL plus outbox and return. Downstream services recover from their own backlog. This improves failure isolation, although event freshness becomes an SLO.

## Modeled topic design

| Topic | Peak rate | Partitions | Key | Consumer groups | Retention |
|---|---:|---:|---|---|---:|
| `item.events.v1` | 500/s | 8 | `itemId` | Cassandra, Search, cache invalidation | 7 days |
| `order.events.v1` | 200/s | 4 | `orderId` | Payment, Notification, Analytics | 30 days |
| `payment.events.v1` | 100/s | 4 | `paymentId` | Order, Ledger, Notification | 90 days |
| `notification.commands.v1` | 500/s | 8 | `userId` | Email, SMS, Push | 3 days |
| `message.events.v1` | 2,000/s burst | 32 | `conversationId` | Delivery, Moderation, Archive | 7 days |

This is 56 logical partitions. At RF=3, it becomes 168 replicas. Partition count must be justified by measured bytes/second, consumer processing rate, replay deadline, ordering, and expected growth—not by choosing a large number for an interview.

## Event bandwidth

Assuming 2 KB per event at simultaneous peak:

```text
Item:          500 × 2 KB = 1.0 MB/s
Order:         200 × 2 KB = 0.4 MB/s
Payment:       100 × 2 KB = 0.2 MB/s
Notification:  500 × 2 KB = 1.0 MB/s
Message:     2,000 × 2 KB = 4.0 MB/s
Total:                        6.6 MB/s
```

With RF=3, raw replication traffic is roughly 19.8 MB/s before overhead. Sustaining 6.6 MB/s for a whole day would create about 570 GB logical data/day, so retention sizing must use actual average rates and compression, not simultaneous peaks.

---

# Page 11 — Kafka Topics, Partitions, Keys, and Consumers

## Partition sizing

If a Cassandra projection consumer is benchmarked at 100 events/s and item events peak at 500/s:

```text
minimum partitions = ceil(500 / 100) = 5
configured partitions = 8
concurrency headroom = (8 - 5) / 5 = 60%
```

The 100 events/s figure must come from a consumer benchmark. Eight partitions allow at most eight active consumers in one traditional consumer group. A ninth consumer remains idle.

## Key choice

- Item key: `itemId`, preserving item lifecycle order.
- Order key: `orderId`, preserving Saga state order.
- Payment key: `paymentId`, preserving authorization/capture/refund order.
- Notification key: `userId`, limiting per-user reordering.
- Message key: `conversationId`, preserving conversation order.

Kafka guarantees order inside a partition, not across a topic. The [Kafka introduction](https://kafka.apache.org/documentation/) documents keyed partition placement and per-partition ordering.

## Consumer groups

Different logical purposes receive different groups. For example, `item.events.v1` may have `item-cassandra-v1`, `item-search-v1`, and `item-cache-invalidation-v1`. Each group maintains independent offsets and consumes every event. Instances inside one group divide partitions.

The current repository uses one projection group. With the default listener concurrency, one instance normally consumes its assigned partitions on one listener thread; six partitions provide potential parallelism, not six automatic threads.

## Offset and delivery semantics

The repository disables auto commit and uses record acknowledgement mode. Processing can still happen twice if Cassandra succeeds and the process crashes before offset commit. Therefore the consumer compares aggregate versions, and side-effect consumers should also store processed `eventId` values.

The correct description is **at-least-once delivery with idempotent effects**, not global exactly once.

## Producer durability

The producer uses `acks=all` and idempotence. In a production cluster, RF=3 with `min.insync.replicas=2` means two in-sync replicas must acknowledge a write; one broker may fail while acknowledged data remains available. The local single-broker RF=1 environment still contains only one copy. See [Kafka producer configuration](https://kafka.apache.org/39/configuration/producer-configs/) and [broker configuration](https://kafka.apache.org/39/configuration/broker-configs/).

## Deep questions

### What causes consumer lag?

Processing is slower than arrival, a consumer is unhealthy, rebalances pause work, Cassandra is slow, a poison record repeatedly fails, or a hot partition receives disproportionate traffic. If arrival is 500/s and processing is 400/s, lag grows by 100/s, or 360,000 events/hour.

### How do you handle poison messages?

Classify transient and permanent errors. Retry transient dependency failures with bounded exponential backoff. Send invalid schema/business records to a dead-letter topic with original topic, partition, offset, key, error, and trace ID. Alert on DLQ rate and provide a controlled replay tool.

### How do you avoid a retry storm?

Do not block every consumer indefinitely or retry immediately. Use retry topics such as `item.events.retry.1m` and `.retry.10m`, cap attempts, add jitter, and open a circuit breaker when Cassandra is unhealthy. Kafka lag is safer than thousands of synchronized dependency calls.

### What happens during rebalance?

Partitions move between consumers and processing may pause. In-flight work may be redelivered depending on commit timing. Keep poll-thread work bounded, configure timeouts from processing duration, use cooperative assignment where appropriate, and ensure consumers remain idempotent.

### How do you evolve event schemas?

Every event contains event ID, event type, schema version, aggregate ID/version, timestamp, and payload. Consumers tolerate additive fields. Breaking meaning or type changes require a new version or dual-publish/migration plan. A schema registry is appropriate for Avro/Protobuf contracts when governance requires it.

---

# Page 12 — Transactional Outbox

## What I say

The outbox solves the database/Kafka dual-write problem. Item Service stores business changes and publication intent in one PostgreSQL transaction. A relay publishes pending rows and marks them complete. Kafka downtime delays projection but does not erase the committed event.

## Implemented flow

```text
@Transactional createItem
    ├─ INSERT items
    ├─ INSERT inventory
    └─ INSERT outbox_events status=PENDING

@Scheduled relay every completed-run + 1 second
    ├─ SELECT oldest 50 PENDING
    ├─ Kafka send, key=itemId
    ├─ wait up to 10 seconds
    └─ mark PUBLISHED or increment attempt_count
```

`enqueue` uses `Propagation.MANDATORY`; status updates use `REQUIRES_NEW`. The `(status,created_at)` index matches the poll query.

## Deep questions

### Why separate OutboxService, Publisher, and EventProducer?

OutboxService owns database state. Publisher owns polling, retry, and status transitions. EventProducer owns serialization and Kafka transport. This separation gives clear transaction boundaries and testability.

### Why does producer return `CompletableFuture`?

Kafka acknowledgement arrives asynchronously. Returning the future lets a caller compose callbacks or wait. The current relay waits with a ten-second bound because it must know the send result before marking the row published. The [Java CompletableFuture API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html) defines completion-stage behavior.

### What is the throughput of the current relay?

It cannot be proven from batch size alone. It processes at most 50 rows per pass, waits sequentially for each send, then waits one second after completion. With near-zero send latency its static ceiling is below 50 events/s; with 20 ms acknowledgements, 50 sends consume about one second plus the one-second delay, approximately 25 events/s. That is a model, not a benchmark.

### How would you scale it?

Use bounded asynchronous sends, claim rows with `FOR UPDATE SKIP LOCKED`, run multiple relay instances, partition claims by aggregate hash, or use CDC such as Debezium. Preserve per-aggregate order, add retry scheduling and DLQ, archive published rows, and monitor oldest pending age.

### Why is delivery still at least once?

The process can crash after Kafka acknowledges but before PostgreSQL becomes `PUBLISHED`. The row is retried, producing a duplicate. Producer idempotence does not atomically cover the application database, so consumers must be idempotent.

---

# Page 13 — Order Placement, Multithreading, and Thread Pools

## What I say

Multiple HTTP users already execute concurrently on Spring MVC container threads. I do not create a new thread per request or parallelize item lines with `parallelStream`. The production design persists a `PENDING` order and outbox first, then a bounded worker pool processes different orders concurrently.

One worker owns one order workflow. It calls one batch inventory reservation, initiates an idempotent payment, and writes short state transitions. The durable Kafka command or database job remains recoverable if a process restarts.

## Concurrency sizing

At 50 peak orders/s and assumed 400 ms workflow time:

```text
required concurrency = 50 × 0.4 = 20 orders
```

With three instances and eight workers each:

```text
3 × 8 = 24 concurrent orders
24 / 0.4 = 60 orders/s modeled capacity
```

This gives 20% modeled headroom. Real sizing must use p95/p99 processing time, payment latency, database pool wait, and Kafka lag.

## Worker workflow

```text
1. Controller validates Idempotency-Key and authenticated user.
2. Short transaction inserts PENDING order + unique key + outbox.
3. API returns 202 and orderId.
4. Kafka partitions order command by orderId.
5. Listener concurrency processes different orders.
6. One batch command reserves every line under reservationId.
7. Payment authorizes under payment idempotency key.
8. Short transaction marks CONFIRMED and writes outbox.
9. Failure persists compensation commands and retry schedule.
```

Kafka listener concurrency can serve as the worker pool. If a custom `ThreadPoolTaskExecutor` is required, it should execute only durable, claimable order IDs—not anonymous lambdas that disappear on restart. Spring documents `ThreadPoolTaskExecutor` and `@Async` in its [task execution guide](https://docs.spring.io/spring-framework/reference/integration/scheduling.html).

## Why not parallelize the items inside one order?

An eight-item order would issue eight simultaneous reservations. At 50 orders/s that can create 400 reservation calls/s before retries. Partial success creates a harder compensation problem. A batch inventory command reduces round trips and gives Item/Inventory Service one place to implement all-or-nothing reservation semantics.

## Thread-pool configuration starting point

For a custom durable-task dispatcher per instance:

```java
executor.setCorePoolSize(8);
executor.setMaxPoolSize(8);
executor.setQueueCapacity(32);
executor.setThreadNamePrefix("order-worker-");
executor.setWaitForTasksToCompleteOnShutdown(true);
```

The fixed eight threads make downstream concurrency predictable. The queue is deliberately small because Kafka or PostgreSQL is the durable backlog. On rejection, the dispatcher must leave/reset the task as pending rather than lose it.

## Deep questions

### Why not annotate the existing method with both `@Async` and `@Transactional`?

An externally proxied async method can open a new transaction in its worker thread, but it does not inherit the caller’s transaction. The in-memory task can disappear on restart, and the transaction still cannot include remote inventory/payment calls. The problem is durability and workflow scope, not that the two annotations are syntactically incompatible.

### Does transaction context propagate to another thread?

No for ordinary imperative Spring transactions. The context is thread-bound. Each worker needs explicit short transactions around local database state. Remote calls occur outside those transactions.

### What queue size should you choose?

If arrival exceeds service capacity by 100 tasks/s, a 500-task in-memory queue fills in five seconds and adds severe latency. A small queue provides local smoothing; Kafka provides durable buffering. Queue wait time and rejection rate matter more than a large nominal capacity.

### How does the DB pool constrain the worker pool?

Forty Java threads with ten database connections still allow only about ten simultaneous DB operations. The workflow should not hold a connection while waiting on Payment. Thread count, HTTP connection pool, Hikari pool, database CPU, and downstream concurrency must be tuned together.

---

# Page 14 — Local Transactions and Distributed Saga

## Local transaction

`@Transactional` protects operations managed by one database transaction manager. Item + Inventory + Outbox is a correct local transaction. Order + OrderItems + OrderOutbox is another. Auth’s local transaction cannot roll back an Account Service HTTP call.

Spring implements declarative transactions through AOP proxies. A successful method commits; configured failures roll back. Self-invocation can bypass the proxy, so transaction boundaries should be external public service methods.

## Inventory correctness

```sql
UPDATE inventory
SET available_quantity = available_quantity - :quantity,
    version = version + 1
WHERE item_id = :itemId
  AND available_quantity >= :quantity;
```

One affected row means success; zero means conflict. With stock 100 and 1,000 concurrent one-unit reservations, the invariant test must produce exactly 100 successes, 900 conflicts, and final stock zero across several Item Service replicas.

## Saga

```text
PENDING
  ├─ reserve stock → STOCK_RESERVED
  ├─ authorize payment → PAYMENT_AUTHORIZED
  └─ confirm order → CONFIRMED

Failure paths:
  ├─ payment rejected → release stock → CANCELLED
  └─ confirmation failure → retry state transition
```

Every command uses a durable idempotency key. Compensation is persisted and retried. Two-phase commit is avoided because it would tightly couple independent services and reduce availability.

## Deep questions

### What if inventory succeeds and Payment times out?

Do not assume payment failed. Keep the order in `PAYMENT_PENDING`, query or consume the provider’s result using the same idempotency key, and release stock only after a final failure. A timeout means outcome unknown, not outcome false.

### How do you avoid double compensation?

Release uses `reservationId` and an idempotent state transition. Inventory stores whether that reservation is active, confirmed, or released. Repeating release returns the already-released result without increasing stock twice.

### Optimistic or pessimistic locking?

Item metadata uses JPA `@Version`, suitable for uncommon conflicts. Inventory uses a conditional atomic update that directly encodes the invariant. Pessimistic `FOR UPDATE` can protect short critical sections but increases blocking and deadlock risk.

---

# Page 15 — WebFlux and API Gateway

## What I say

WebFlux is used at the API Gateway because it can proxy many concurrent network requests using a small event-loop model and Reactive Streams backpressure. The downstream business services use Spring MVC/JPA because JDBC and JPA are blocking. Using WebFlux in front does not make blocking downstream code non-blocking.

Spring describes WebFlux as fully non-blocking with Reactive Streams backpressure and support for Netty in its [official reference](https://docs.spring.io/spring/reference/web/webflux.html).

## Important rule

Do not perform blocking JDBC, filesystem calls, or `.block()` on Gateway event-loop threads. Blocking one event-loop thread delays many unrelated connections. Authentication at the Gateway should use local JWT verification or reactive clients.

## Deep questions

### Why not convert every service to WebFlux?

The current services use JPA/JDBC, which remain blocking. A reactive controller that calls blocking repositories still needs a bounded blocking scheduler and adds complexity. MVC is appropriate for the current transaction-heavy services; WebFlux earns its place at the network-heavy Gateway or streaming endpoints.

### How does backpressure differ from a thread-pool queue?

Reactive backpressure lets a downstream subscriber signal how much data it can process. A bounded executor queue limits accepted tasks. Both prevent unlimited work, but they operate at different abstraction levels. External dependencies still need concurrency limits and timeouts.

### What should Gateway validate?

JWT signature, expiry, issuer, audience, expected algorithm, route authorization, payload limits, and rate limits. It should propagate correlation ID and verified user claims, but each business service still enforces ownership and domain authorization.

---

# Page 16 — JWT and API Security

## Current token

The Auth token contains:

- `sub`: global user ID.
- `id`: user ID claim.
- `email`.
- `roles`: `SELLER` or `BUYER`.
- issuer `auth-server`.
- audience `api-client`.
- issued-at, 48-hour expiration, and unique JWT ID.

The Gateway verifies the shared HMAC signature and expiration through the JWT parser. Issuer and audience are present but not explicitly enforced in current code.

## Performance

Local JWT validation removes a per-request session database/network lookup. At 1,000 peak QPS, that avoids up to 1,000 remote authentication lookups per second. It shifts revocation and stale-role handling into token lifetime, refresh, key rotation, or token-version design.

## Deep questions

### Why is a 48-hour access token risky?

A disabled user or changed seller role may retain old access for up to 48 hours without additional revocation. Production normally uses shorter access tokens, rotating refresh tokens, key rotation, and a response plan for compromised credentials. Exact duration comes from threat model and UX requirements.

### Why must Item Service authorize again?

Gateway validation is an edge control. Item Service owns the invariant that a seller can modify only their own items. Defense in depth protects against route mistakes, internal misuse, and future alternate entry paths.

### What belongs in JWT and what does not?

Stable authorization claims and identity belong in it. Password hashes, addresses, payment details, and frequently changing data do not. Large tokens increase every request’s bandwidth and can expose readable claim data even though the token is signed.

---

# Page 17 — Microservice Communication, REST, gRPC, and GraphQL

## REST/OpenFeign

REST is used for immediate CRUD and reservation commands. Feign keeps contracts declarative and resolves Eureka service names through Spring Cloud LoadBalancer. It reduces boilerplate but still needs connect/read timeouts, error mapping, metrics, circuit breakers, and idempotency.

## gRPC

gRPC is appropriate for controlled internal APIs requiring Protobuf contracts, streaming, or high call volume. It does not solve chattiness. Replacing sixteen REST calls with sixteen gRPC calls is normally less valuable than replacing them with one batch REST call.

## GraphQL

GraphQL can help frontend composition when clients need different fields from several domains, but it introduces query-cost control, N+1 resolver risk, schema governance, and authorization complexity. It should normally sit in a BFF/API composition layer, not replace service-to-service commands.

## Deep questions

### Which calls should be synchronous?

Login, item point reads, idempotency status, and inventory reservation require immediate responses. Search projection, notification, analytics, and recommendations can be asynchronous. Payment initiation may be synchronous while its final result remains event-driven.

### How deep may a synchronous chain become?

Keep the critical path short. Gateway → Order → Inventory/Payment is already sensitive. Do not make Inventory synchronously call Item, Account, Notification, and Analytics. Precompute required data, batch requests, or consume events.

### How do you retry safely?

Retry only transient failures and idempotent operations, within the caller’s deadline, using bounded exponential backoff and jitter. Three immediate attempts at 1,000 QPS can create 3,000 downstream attempts/s. Non-idempotent commands require a stable key and stored result.

---

# Page 18 — Eureka, Docker, Kubernetes, and AWS

## Eureka

Eureka stores service instance addresses under logical names. Gateway routes with `lb://SERVICE-NAME`; Feign calls names such as `ACCOUNT-SERVICE` and `ITEM-SERVICE`; Spring Cloud LoadBalancer selects an instance.

Docker Compose DNS provides name resolution but not the entire Eureka lease/registry model. Kubernetes already provides Services and EndpointSlices, so a Kubernetes deployment commonly removes Eureka and uses platform discovery.

## Current local deployment

The Compose file defines PostgreSQL, Redis, Kafka, Cassandra, Cassandra initialization, Eureka, Account, Auth, Item, Order, Gateway, and frontend: 12 container definitions, of which Cassandra initialization is one-shot.

The local Kafka and Cassandra topology has one node and RF=1. It validates integration behavior, not high availability.

## AWS mapping — proposed, not repository-backed

| Capability | Possible AWS deployment |
|---|---|
| Containers | EKS or ECS |
| PostgreSQL | RDS/Aurora PostgreSQL Multi-AZ |
| Redis | ElastiCache for Redis |
| Kafka | Amazon MSK |
| Cassandra | Managed Cassandra-compatible service or self-managed multi-AZ cluster after compatibility review |
| Static frontend/images | S3 + CloudFront |
| Secrets | Secrets Manager/KMS |
| Metrics/logs | CloudWatch plus Prometheus/Grafana/OpenTelemetry stack |

## Deep questions

### How do you deploy without downtime?

Use readiness probes, rolling or canary deployment, backward-compatible APIs/events, and expand-migrate-contract schema changes. Do not terminate an instance until HTTP requests, Kafka records, and outbox work are safely drained or recoverable.

### How do you autoscale?

HTTP services scale on CPU plus request concurrency/latency. Kafka consumers scale on lag and processing rate but cannot use more active consumers than partitions. PostgreSQL and Redis capacity must be protected because scaling application replicas also multiplies connection pools and cache traffic.

### What is the disaster-recovery plan?

Define RPO/RTO, automated PostgreSQL backups and restore tests, Cassandra repair/backups, Kafka replication/retention, infrastructure-as-code, and regional failover rules. A backup that has never been restored is not proven recovery.

---

# Page 19 — AOP

## What I say

AOP applies cross-cutting behavior around selected method calls through proxies. The repository does not define a custom `@Aspect`, but Spring AOP already powers `@Transactional`, `@Cacheable`, `@CachePut`, and `@CacheEvict`. Those annotations materially change transaction and Redis behavior without duplicating infrastructure code in each method.

For production I would add narrow aspects or interceptors for latency metrics, correlation, audit fields, and consistent exception mapping. Core payment and inventory rules stay explicit in services.

## Deep questions

### What is self-invocation?

An internal `this.someAnnotatedMethod()` call bypasses the default Spring proxy, so transaction, cache, or async advice on the second method may not run. Put the boundary on the externally called public method or move it to another bean.

### Can AOP hurt performance?

Proxy invocation overhead is normally small compared with network/database calls, but advice can be expensive if it serializes payloads, logs synchronously, or emits high-cardinality metrics. I would require less than 1% throughput impact in an enabled/disabled load comparison rather than assume it is free.

### What is the transaction/cache ordering problem?

If a cache write becomes visible before a transaction commits and the transaction later rolls back, clients may see nonexistent data. Use after-commit invalidation, transaction synchronization, or the committed outbox event. Test rollback paths, not only successful writes.

---

# Page 20 — Monitoring, Logging, and Alerting

## Metrics

| Layer | Important metrics |
|---|---|
| Gateway/API | QPS, status, p50/p95/p99, active requests, timeouts, rate-limit rejects |
| JVM | CPU, heap, allocation, GC pause, live threads, executor active/queue/reject |
| PostgreSQL | pool active/wait, slow SQL, locks, deadlocks, buffers, WAL, replication lag |
| Cassandra | read/write p99, timeouts, tombstones, compaction, disk, dropped mutations |
| Redis | L1/L2 hit rate, latency, memory, evictions, expirations, hot keys |
| Kafka | publish latency/errors, ISR, partition skew, consumer lag, rebalances, DLQ |
| Business | order intake, stock conflict, duplicate prevention, payment success, Saga age |

## Logging

Use structured JSON logs containing timestamp, service, environment, trace ID, span ID, route/operation, order ID where appropriate, result, duration, and safe exception classification. Never log passwords, JWTs, payment details, or full personal addresses. High-cardinality identifiers belong in logs/traces, not general metric tags.

## Tracing

Propagate W3C trace context through Gateway, Feign, and Kafka headers. A checkout trace should show Gateway time, Order processing, batch Inventory, Payment, database spans, and event publication. Async consumer spans link to the producing trace rather than pretending they are one synchronous call.

## Alerts

- Checkout error rate or p99 violates SLO for a sustained window.
- PostgreSQL connection wait exceeds 10 ms p99 or pool saturation remains high.
- Redis hit rate falls below the expected baseline and origin QPS rises.
- Kafka lag threatens the five-second projection freshness target.
- Oldest pending outbox event exceeds ten seconds normally.
- Payment/Saga remains pending beyond its business deadline.
- Cassandra tombstone warnings, compaction backlog, or disk thresholds rise.

The numerical thresholds are initial SLO design values and must be calibrated to real baselines.

## Deep questions

### What do you check first during an incident?

Start with user-facing error rate and p99, then use traces to identify the failing hop. Check saturation rather than only CPU: connection wait, executor queue, downstream timeouts, Redis miss amplification, Kafka lag, and database locks. Low CPU can coexist with total outage when threads wait on a dependency.

### How do you prevent metric-cardinality explosion?

Do not tag metrics with `userId`, `itemId`, `orderId`, raw URLs, or exception messages. Use bounded route templates, status classes, service names, and error categories. Put individual identifiers in traces and logs.

---

# Page 21 — Failure Scenarios

## PostgreSQL commits but Kafka is down

The outbox remains `PENDING`, the relay retries, and Cassandra/Search lag grows. Alert on oldest pending age. The business record is not lost.

## Kafka publishes twice

Consumers compare aggregate versions or persist processed event IDs. Payment and notification effects use unique business keys. Duplicate delivery must not double charge, double stock, or double-send a one-time benefit.

## Redis is down

Bypass cache and use bounded origin fallback. At the modeled 85% hit rate, origin load can jump from 105 to 700 QPS, 6.67×. Apply bulkheads and load shedding so a cache outage does not become a database outage.

## Cassandra is down

PostgreSQL item writes continue, Kafka retains events, and consumer lag grows. Selected reads may fall back to PostgreSQL under a strict concurrency cap. When Cassandra recovers, consumers catch up or projections rebuild.

## Payment response is lost

Treat the result as unknown. Query by idempotency key or consume the provider’s durable result. Never issue a new charge with a new key simply because the client timed out.

## One Order Service instance dies

HTTP traffic moves to healthy instances. Kafka reassigns partitions. Uncommitted records are processed again, so workflows must be idempotent. In-memory-only tasks would be lost, which is why commands are persisted first.

## A hot item receives massive traffic

Redis/Caffeine absorb reads, but inventory writes still serialize on the authoritative row. Use a reservation service, admission queue, per-item rate limit, or tokenized stock buckets for extreme flash sales. Do not rely on a JVM lock across replicas.

## Deep question: how do you test failures?

Stop Kafka after PostgreSQL commit and prove outbox recovery. Deliver one event twice and verify one effect. Race 1,000 reservations against stock 100. Kill Order Service after inventory reservation and verify durable compensation. Disable Redis and confirm origin protection. Slow Payment and verify deadline, retry, and circuit-breaker behavior.

---

# Page 22 — Performance Improvements and Achievements

Use only achievements you actually implemented or measured. The table separates code-derived improvement from performance claims.

| Change | Before | After design | Defensible numeric statement |
|---|---:|---:|---|
| Batch checkout | `2N` Item calls | 1–2 calls | 8 lines: 16 → 1–2, 87.5%–93.75% fewer calls |
| Batch inventory read | `N` SQL lookups | 1 lookup | 100 items: 100 → 1, 99% fewer DB round trips |
| Redis/Caffeine | 700 origin reads/s | 105 at 85% hit | 595 QPS / 85% origin-read reduction |
| Async side effects | 4 mandatory dependencies | Outbox + Kafka | Modeled availability avoids `0.999⁴ ≈ 99.60%` critical path |
| Cassandra seller buckets | 1M-row unbounded seller | 16 partitions | ~62,500 rows and 62.5 MB/partition at 1 KB/row |
| Order workers | sequential per worker | 24 concurrent | At 400 ms average, modeled 60 orders/s |
| Redis DAU | exact set grows with users | HLL | ≤12 KB/day sketch, 0.81% standard error |

These figures prove reductions in operations or give capacity math. They do not prove p99 improvement until the change is implemented and benchmarked.

## Strong achievement answer

> I reduced unnecessary work in the critical paths rather than solving every problem with more threads. I identified that an eight-line checkout generated sixteen service calls and redesigned it as a one- or two-call batch reservation, reducing network round trips by 87.5%–93.75%. I also changed the inventory read plan from one query per item to a batch lookup, reducing a 100-item page from 100 inventory round trips to one. I would report the latency improvement only after reproducing the same workload before and after.

---

# Page 23 — Rapid Deep-Dive Questions

## Why is `userId` stored on Item?

It identifies the seller owner and supports authorization and seller-scoped queries. Item Service takes the authenticated token subject rather than trusting an arbitrary request-body user ID.

## Why not copy seller ID into every inventory row?

Inventory is keyed by item ID and belongs to the same service boundary. Copying seller ID creates another field that can drift. Ownership checks use the authoritative Item row; denormalization is justified only for a measured query.

## Why is item ID a UUID string?

It allows ID generation without a central sequence and distributes Cassandra point partitions. Random IDs have larger PostgreSQL indexes and poorer locality than sequential values, so UUIDv7 or another time-ordered identifier could improve write locality while remaining decentralized.

## Why store money as `BigDecimal`/numeric?

Binary floating point cannot exactly represent common decimal amounts. The schema uses precision 19 and scale 2. Currency is stored separately, and an order rejects mixed currencies. Production pricing may need minor-unit integers, tax/discount snapshots, and explicit rounding rules.

## Why copy item price into OrderItem?

An order is a historical financial record. Re-reading current catalog price would change history. Order lines snapshot item ID, name, price, currency context, and quantity at purchase time.

## Why use event aggregate version?

Kafka preserves order only within a partition, and retries/rebuilds can deliver duplicates. Aggregate version lets a projection ignore older/equal updates. Event ID handles duplicate side effects; both fields have different purposes.

## Why cache item lists carefully?

A single global list key is invalidated by every item write and can become large. Production caches pages/query keys, limits page size, adds TTL jitter, and may cache item IDs separately from item details.

## Why is Cassandra `findAll()` dangerous?

It does not supply a partition key and can scan across the cluster. Production catalog reads use seller/category/time bucket keys and bounded limits. Cassandra performance comes from predictable partition access, not from replacing SQL syntax.

## Why is `FetchType.EAGER` risky for order lines?

A history page of 50 orders averaging 10 lines materializes about 500 line objects even if the UI needs only headers. Use a summary projection for lists and fetch lines for one order detail.

## Why use a partial index for pending outbox rows?

Most retained rows become `PUBLISHED`. Indexing only pending rows keeps the hot polling structure smaller and reduces maintenance, while historical published rows remain accessible through other paths.

## Why not keep a transaction open during Payment?

Network latency makes transaction duration unpredictable, consumes a connection, increases lock lifetime, and still cannot roll back the remote provider. Persist state before the call and record the result in a new short transaction.

## Why not rely on Redis distributed locks for stock?

Locks introduce lease-expiry and ownership problems, while the PostgreSQL conditional update already performs check-and-decrement atomically. Redis remains useful for rate limiting and idempotency acceleration, not the final stock invariant.

## Why not claim Kafka exactly once?

Kafka can provide exactly-once semantics inside defined Kafka operations, but this workflow crosses PostgreSQL and Cassandra. A crash after Kafka acknowledgement but before outbox status update produces a duplicate. Idempotent consumers are still required.

## What happens when partitions increase?

New records may map keys to different partition numbers, and there is still no order across partitions. Existing records do not move. Consumers rebalance, and capacity improves only if enough consumers and downstream capacity exist.

## How do you prevent one message conversation from becoming hot?

Conversation ID preserves order but constrains one conversation to one partition. If a single conversation exceeds partition capacity, product semantics must permit substreams or sequence-based merge; otherwise that conversation’s ordered throughput is physically bounded.

## What is the difference between rate limiting and backpressure?

Rate limiting controls admitted request rate, often by user or API. Backpressure controls how much outstanding work flows through a dependency. The platform needs both: Gateway token buckets and bounded worker/connection pools.

## How do you size a thread pool for CPU work?

Start near available cores because more CPU-bound threads add context switching. For blocking I/O, use measured wait/compute ratio. An eight-core service with 40 ms wait and 10 ms compute gives `8 × (1 + 40/10) = 40` as a test starting point, not a universal production setting.

## What is a cache hit-rate trap?

A 95% global hit rate can hide misses on the most expensive endpoint or a hot-key stampede. Measure hit rate, latency, and origin amplification by cache and operation. Verify correctness during invalidation and failure, not only steady-state speed.

## How do you version APIs?

Prefer backward-compatible additive changes, tolerant readers, and contract tests. Version only when breaking semantics require it. API, database, and event schema versions are separate lifecycles.

## How do you handle personally identifiable information?

Minimize collection, encrypt in transit and at rest, restrict fields by service identity, redact logs, define retention/deletion, and audit access. Kafka and backup retention must participate in deletion policy; removing one PostgreSQL row is insufficient.

## What would make you split Inventory from Item Service?

Independent scaling, a dedicated flash-sale workload, different availability/SLO, separate team ownership, or more complex reservation lifecycle. Until then, inventory can remain a strong internal module owned by Item Service without exposing its table to Order Service.

---

# Page 24 — Final Two-Minute Answer

## Use this only with claims you can defend

> The platform is a Java 17 and Spring Boot commerce system serving—or, if the number is a design target, designed for—one million daily active users, about 200 average API QPS, and 1,000 peak QPS. Its business boundaries include Gateway, Auth, Account, Item, Inventory, Cart, Order, Payment, Search, Notification, Messaging, and Recommendation.
>
> PostgreSQL is the transactional source of truth. Cassandra stores denormalized item projections, Redis supplies shared caching and idempotency acceleration, and Kafka decouples projection, search, payment, notification, and message workflows. Item writes commit metadata, inventory, and an outbox record atomically. Kafka uses entity IDs as keys to preserve local order, and consumers are idempotent because end-to-end delivery is at least once.
>
> The capacity model uses five principal Kafka topics and 56 logical partitions. Catalog traffic peaks around 700 QPS; with an 85% multi-layer cache-hit target, origin reads fall to approximately 105 QPS. Seller catalog data uses Cassandra buckets so an extreme one-million-item seller is split into sixteen partitions of about 62,500 rows each under the sizing assumption.
>
> Inventory correctness does not depend on cache or JVM locks. PostgreSQL performs a conditional atomic decrement, so a concurrency test with stock 100 and 1,000 one-unit requests must produce exactly 100 successes and 900 conflicts. Orders use durable idempotency and a Saga; worker threads process different orders, while one order uses a batch inventory command rather than parallel calls for each line.
>
> My main optimization principle is to remove work before adding hardware. An eight-line order currently creates sixteen Item calls; batching reduces that to one or two. A 100-item page currently creates 100 inventory lookups; batch loading reduces them to one. I distinguish those derived reductions from measured latency and validate the 1,000-QPS target with reproducible p95/p99 load, soak, and failure tests.

---

# Official References

- [Apache Kafka introduction and core concepts](https://kafka.apache.org/documentation/)
- [Apache Kafka producer configuration](https://kafka.apache.org/39/configuration/producer-configs/)
- [Apache Kafka broker configuration](https://kafka.apache.org/39/configuration/broker-configs/)
- [Apache Kafka consumer groups](https://kafka.apache.org/38/javadoc/org/apache/kafka/clients/consumer/KafkaConsumer.html)
- [Apache Cassandra data modeling](https://cassandra.apache.org/doc/stable/cassandra/developing/data-modeling/intro.html)
- [PostgreSQL transaction isolation](https://www.postgresql.org/docs/current/transaction-iso.html)
- [PostgreSQL EXPLAIN](https://www.postgresql.org/docs/current/using-explain.html)
- [PostgreSQL multicolumn indexes](https://www.postgresql.org/docs/current/indexes-multicolumn.html)
- [Spring transaction management](https://docs.spring.io/spring-framework/reference/data-access/transaction.html)
- [Spring task execution and scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Spring WebFlux](https://docs.spring.io/spring/reference/web/webflux.html)
- [Spring Cloud OpenFeign](https://docs.spring.io/spring-cloud-openfeign/reference/spring-cloud-openfeign.html)
- [Java CompletableFuture](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html)
- [Redis cache-aside](https://redis.io/docs/latest/develop/use-cases/cache-aside/)
- [Redis SET command](https://redis.io/docs/latest/commands/set/)
- [Redis HyperLogLog](https://redis.io/docs/latest/develop/data-types/probabilistic/hyperloglogs/)
- [Caffeine eviction and sizing](https://github.com/ben-manes/caffeine/wiki/Eviction)
