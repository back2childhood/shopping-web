# Walmart-Style Java Microservices Interview Q&A

This document prepares answers to the questions from the interview while grounding them in this shopping-platform project. The primary service used in the examples is **Item Service**, with API Gateway, Auth Service, Account Service, and Order Service used when the question crosses service boundaries.

## How to use these answers

Give the first paragraph under **Interview answer** first. Continue into the detailed explanation only when the interviewer asks a follow-up. Keep three kinds of numbers separate: a value that exists in configuration, a measured production result, and a capacity-planning target. In this repository, Java 17, the three-second Gateway time limit, the six-partition local Kafka default, the executor sizes, and the endpoint counts are configuration facts. One million daily users, 200 average API QPS, 1,000 peak QPS, 80,000 orders per day, and 50 peak orders per second are the consistent capacity model used for design exercises; they should be called production measurements only if you actually have dashboards or load-test reports that prove them.

WCP and CCM are Walmart-specific platform terms whose exact application IDs and workflows are not represented in this repository. Do not invent internal identifiers. Explain the equivalent implementation in this project, then describe how you would map it to the employer's platform.

## Project facts to remember

| Topic | Project-specific answer |
|---|---|
| Main service | `ITEM-SERVICE`, exposed through API Gateway |
| Other active services | `API-GATEWAY`, `AUTH-SERVICE`, `ACCOUNT-SERVICE`, `ORDER-SERVICE`, `EUREKA-SERVER` |
| Java | Java 17 in Maven and Docker runtime images |
| Spring | Spring Boot 3.5.7 and Spring Cloud 2025.0.1 |
| Gateway timeout | Three-second Resilience4j time limit; two seconds counts as a slow call |
| Main persistence | PostgreSQL is the source of truth for item and inventory writes |
| Read optimization | Cassandra item projection plus Redis cache |
| Messaging | Kafka topic `item.events.v1`, keyed by item ID |
| Consumer group | `item-service-projection-v1` |
| Kafka acknowledgement | Producer `acks=all` and idempotence enabled; consumer auto-commit disabled with record acknowledgement mode |
| Local Kafka partitions | Compose default is six partitions |
| Order executor | Core 8, maximum 32, queue 200, keep-alive 60 seconds, `CallerRunsPolicy` |
| Deployment in this repository | Docker images and Docker Compose; no Kubernetes manifests or WCP deployment definition |
| Monitoring today | Spring Boot Actuator with `health` and `info`; Prometheus/Grafana integration is described below but is not currently wired into the repository |

---

# First interviewer

## 1. What is your service called in WCP?

### Interview answer

My primary deployable is Item Service. Its Spring application name and Eureka discovery name are `ITEM-SERVICE`, and it owns product metadata, seller ownership, authoritative inventory, item event publication, Cassandra projections, and item caches. This repository is containerized with Docker Compose rather than deployed to Walmart Cloud Platform, so I would not invent a WCP application ID. In WCP I would use the application and service identifiers assigned by the team, for example `<actual-application-id>/<actual-service-id>`, and verify the environment and namespace before changing it.

### What the interviewer is checking

This is usually an ownership and operational-awareness question. The interviewer wants to know whether you can identify the exact deployable you support, distinguish an application from a service and environment, and locate it in the deployment platform. If you have real WCP access, give its exact registered name, owning team, production environment, region, and dashboard. If you do not, connect the question to your actual deployment model instead of claiming access to an internal platform.

In this project, the logical name is stored in `spring.application.name`. Docker Compose gives the container the network name `item-service`, while Eureka publishes `ITEM-SERVICE`. These names solve related but different problems: Docker DNS resolves a container on the local bridge network, and Eureka lets Feign or Gateway resolve one or more application instances by logical service name.

## 2. What is the timeout for your service?

### Interview answer

The externally visible route timeout is three seconds at API Gateway. Resilience4j also classifies a call taking more than two seconds as slow; if at least five calls have been observed and 50% are failures or slow calls within the ten-call window, the circuit breaker opens. I separate this request deadline from health-check timeouts, database query timeouts, Kafka producer acknowledgement timeouts, and connection-pool wait time because they protect different resources.

### Follow-up details

The three-second value is defined under `resilience4j.timelimiter.configs.default.timeout-duration`. The Gateway fallback returns HTTP 503 rather than a false HTTP 200. The Compose health checks also use three-second or five-second shell timeouts, but those are probe limits and are not the API service-level timeout.

The current Feign clients do not define explicit connect and read timeouts in their client configuration; `ItemClientConfig` currently adds the internal-service token only. A production hardening step is to set explicit per-client budgets, such as a short connection timeout and a read timeout below the caller's remaining three-second deadline. I would choose those numbers from measured p95 and p99 latency rather than repeat three seconds at every layer, because nested three-second timeouts can turn one request into a much longer synchronous chain.

## 3. How many pods does your service have?

### Interview answer

In the checked-in project there are no Kubernetes Pods; Docker Compose starts one Item Service container. For the production capacity model I use three Item Service replicas across failure domains, but that is a sizing assumption rather than a Kubernetes deployment currently present in the repository. With three replicas, one replica can be unavailable while the other two continue to serve traffic, provided their combined capacity and the database connection limits were tested for that failure case.

### Follow-up details

Do not answer only with a number. Explain the reason for the replica count and the scaling signal. I would start with three replicas for availability, distribute them across nodes or zones, and autoscale from CPU only when CPU correlates with load. For Item Service I would also watch request p95/p99, active requests, Hikari connection wait, Kafka consumer lag, executor saturation, and Redis miss amplification.

Kafka consumers have a separate scaling ceiling: within one consumer group, active consumers cannot exceed the number of assigned partitions. With six partitions, more than six projection consumers in the same group cannot increase partition-level parallelism.

## 4. How many services do you know in Walmart? Can you name some?

### Interview answer

I would not claim the number of Walmart's internal services because it is not public project knowledge and a large retailer can have thousands of deployables. I understand the major commerce domains: customer identity and account, catalog and item, price and promotion, inventory and availability, cart, checkout, order, payment, fraud, fulfillment, shipping, returns, notification, search, recommendation, seller, tax, and address validation. In my shopping platform, the corresponding boundaries include Gateway, Auth, Account, Item/Inventory, Cart, Order, Payment, Search, Notification, Messaging, and Recommendation.

### Follow-up details

Service boundaries should be based on ownership and business invariants rather than creating one service for every database table. Item and Inventory may be separate deployables at large scale because catalog reads and stock contention have very different traffic patterns, but they still require clear ownership. Order must not connect directly to another service's database; it calls a versioned API or sends a command/event.

If the interviewer expects names from a team you actually worked with, name only services you called, consumed events from, supported, or saw in documented architecture. Then state the direction of the dependency. For example, “Order was downstream of Checkout, called Inventory and Payment synchronously for decisions, and published confirmed-order events for Fulfillment and Notification.”

## 5. What is an HTTP status code, and what is 401?

### Interview answer

An HTTP status code is the server's standardized summary of the result of a request. The classes are 1xx informational, 2xx successful, 3xx redirection, 4xx client-side request or authorization problems, and 5xx server or dependency failures. HTTP 401 means the request is not authenticated with acceptable credentials. In this project, API Gateway returns 401 when the Authorization header is missing, malformed, expired, or has an invalid JWT signature.

HTTP 403 is different: the caller is authenticated, but is not allowed to perform that operation. A buyer with a valid JWT receives 403 when attempting to create or modify a seller's product. HTTP 404 means the resource is not found; 409 represents a state conflict such as insufficient stock or duplicate registration; 400 represents invalid input; 503 represents temporary service unavailability or an open circuit breaker.

### Project examples

| Status | Project example |
|---:|---|
| 200 | Successful item/order read or the current synchronous order creation response |
| 400 | Invalid DTO or mixed currencies in one order |
| 401 | Missing, malformed, invalid, or expired bearer token at Gateway |
| 403 | Buyer attempts seller operation, or seller attempts to modify another seller's item |
| 404 | Item or order ID does not exist |
| 409 | Insufficient inventory or duplicate email |
| 503 | Gateway circuit-breaker fallback when a downstream service is unavailable |

## 6. Did you do deployment?

### Interview answer

I created multi-stage Dockerfiles for the Java services and a Compose deployment that starts PostgreSQL, Redis, Kafka, Cassandra, Eureka, five application services, and the frontend on one network. The deployment includes persistent volumes, environment-specific connection values, dependency health checks, Cassandra initialization, internal and external Kafka listeners, and service readiness ordering. I built the images, ran the full stack, verified the APIs through Gateway, and inspected PostgreSQL, Cassandra, Redis, and Kafka state from their containers.

This is a real container deployment workflow, but it is not the same as saying I deployed to WCP or Kubernetes. For a production platform, my pipeline would build an immutable image, scan it, publish it to a registry, apply versioned configuration and database migrations, deploy to a lower environment, run smoke and contract tests, and then use a rolling or canary production rollout with readiness gates and rollback criteria.

### Deployment checklist I would describe

1. Build from a fixed Git commit and run Maven tests plus the frontend production build.
2. Build immutable images tagged with commit SHA, scan dependencies and images, and push them to the approved registry.
3. Apply backward-compatible database and configuration changes.
4. Deploy to a non-production environment and verify `/actuator/health`, API smoke tests, Kafka connectivity, and schema compatibility.
5. Roll out gradually, watch error rate, p95/p99, saturation, consumer lag, outbox age, and business success rate, then promote or roll back.

## 7. How about CCM configuration? Do you do it before deployment or after, and why?

### Interview answer

If CCM means the team's centralized configuration management platform, I prepare and validate required configuration before application rollout because a new Pod must be able to start with valid URLs, credentials, timeouts, feature settings, and resource limits. I do not blindly replace configuration first. I use an expand-and-contract sequence: publish configuration that is compatible with both old and new application versions, deploy the code, verify it, switch any feature flag gradually, and remove obsolete keys only after old instances are gone.

In this project, the equivalent mechanism is `application.yml` with environment-variable overrides supplied by Compose. Examples are `EUREKA_URL`, `SPRING_DATASOURCE_URL`, `KAFKA_BOOTSTRAP_SERVERS`, `CASSANDRA_CONTACT_POINTS`, `REDIS_HOST`, `JWT_SECRET`, and `INTERNAL_SERVICE_TOKEN`. Non-secret defaults make local development easy; production secrets must come from a secrets manager and must not be committed to Git or stored as plain CCM values.

### Before versus after deployment

Configuration required at startup must exist before or atomically with the deployment. A missing datasource URL or secret makes the new instance fail readiness. A dynamic feature flag can be changed after deployment, but it still needs review, audit, gradual rollout, monitoring, and an immediate rollback value. If a new service version changes the meaning or type of a key, I introduce a new key rather than silently changing a value that old instances still read.

## 8. Have you done database deployment?

### Interview answer

For this project I provisioned PostgreSQL through Compose, created separate schemas for Account, Auth, Item/Inventory, and Order, and initialized the Cassandra `item_service` keyspace and projection table through `docker/cassandra/init.cql`. Hibernate currently uses `ddl-auto=update` for convenient local schema creation. I would not use that as the production database deployment process; production changes should be versioned with Flyway or Liquibase for PostgreSQL and reviewed CQL migrations for Cassandra.

### Safe database rollout

I use expand–migrate–contract. First, add nullable columns, new tables, or new indexes in a backward-compatible migration. Second, deploy code that can work with both old and new shapes. Third, backfill in bounded batches and monitor locks, replication lag, and write amplification. Fourth, switch reads or feature flags. Finally, remove the old column or constraint in a later release after every old service version has stopped.

For Item Service, PostgreSQL is authoritative and the outbox row commits with the Item and Inventory rows. Cassandra is a rebuildable read projection. If Cassandra is unavailable during deployment, PostgreSQL commits and Kafka/outbox retain publication intent; when Cassandra recovers, the consumer catches up idempotently using aggregate version.

## 9. What Java version does your team use?

### Interview answer

This project targets Java 17. The parent Maven build sets `java.version` to 17, and every active Java service uses a Temurin 17 build image and a Java 17 JRE runtime image. Java 17 is an LTS release and supports the Spring Boot 3 codebase while giving us records, text blocks, modern switch expressions, improved garbage collectors, and a mature production ecosystem.

The important operational point is consistency. Local IDE, CI, Maven compiler target, test runtime, and container runtime should agree. A newer local JDK can expose tooling differences—as happened when Mockito's dynamic agent attachment behaved differently—so CI and the deployable image remain the source of truth.

## 10. What IDE do you use, and what is a profile in IntelliJ?

### Interview answer

I use IntelliJ IDEA for Java and Spring development. I use its Maven view, debugger, HTTP client, database tools, Spring bean navigation, test runner, and run/debug configurations. In this context, a profile usually means a Spring profile selected in an IntelliJ Spring Boot run configuration, such as `local`, `test`, or `prod`. The active profile controls which profile-specific properties and `@Profile` beans Spring loads.

The word can also mean IntelliJ's CPU or memory profiler, which records execution samples, allocation, and call stacks. If the interviewer says only “profile,” I clarify whether they mean a Spring environment profile, a Maven build profile, a run configuration, or performance profiling.

In this repository, configuration currently relies mainly on environment-variable overrides instead of `application-local.yml` and `application-prod.yml`. A good IntelliJ run configuration can set `SPRING_PROFILES_ACTIVE`, environment variables, the JRE, VM options, program arguments, and pre-launch Maven tasks without hard-coding them in application code.

## 11. Where do you store the URL?

### Interview answer

I keep environment-dependent URLs in external configuration, not Java constants. Each service has safe local defaults in `application.yml`, and Compose overrides them with environment variables. Feign clients use logical names such as `ITEM-SERVICE` and `ACCOUNT-SERVICE`; Eureka and Spring Cloud LoadBalancer resolve those names to instances, so business code does not store an IP address.

The project stores the Eureka endpoint in `EUREKA_URL`, the PostgreSQL JDBC connection in `SPRING_DATASOURCE_URL`, Kafka brokers in `KAFKA_BOOTSTRAP_SERVERS`, Cassandra nodes in `CASSANDRA_CONTACT_POINTS`, Redis in `REDIS_HOST` and `REDIS_PORT`, and the browser's Gateway base URL in `NEXT_PUBLIC_API_BASE_URL`. In Kubernetes or WCP, I would inject equivalent values through the approved configuration and secret systems. Credentials, private keys, and tokens belong in a secret manager, not beside URLs in Git.

## 12. How many endpoints do you have, and can you name some?

### Interview answer

Item Service, my primary service, has eight business endpoints. The currently active platform has sixteen domain endpoints across Auth, Account, Item, and Order, plus one Gateway fallback endpoint. I count endpoints by HTTP method and route, because `GET /api/items` and `POST /api/items` are different operations even though the path is similar.

### Item Service: eight endpoints

| Method | Route | Purpose |
|---|---|---|
| POST | `/api/items` | Seller creates an item and initial inventory |
| GET | `/api/items` | Authenticated user browses the catalog |
| GET | `/api/items/{id}` | Read one item |
| GET | `/api/items/mine` | Seller reads their own store |
| PUT | `/api/items/{id}` | Owning seller updates item data and stock |
| DELETE | `/api/items/{id}` | Owning seller deletes an item |
| POST | `/api/items/{id}/increase-stock` | Trusted Order Service compensation releases stock |
| POST | `/api/items/{id}/decrease-stock` | Trusted Order Service reserves/decreases stock |

Auth provides `POST /api/auth/register` and `POST /api/auth/login`. Account provides `POST /api/accounts`, `GET /api/accounts/{id}`, and `GET /api/accounts`. Order provides `POST /api/orders`, `GET /api/orders/{orderId}`, and `GET /api/orders/user/{userId}`. Gateway also has `/fallback`, and each service exposes configured Actuator health and info endpoints; I keep management endpoints separate from the business endpoint count.

## 13. How did you use AI?

### Interview answer

I used AI as a development assistant for repository analysis, explaining unfamiliar framework behavior, generating first drafts of tests and documentation, and proposing refactor options. I did not treat generated code as trusted. I reviewed diffs, checked the code against the service boundary and data model, ran the Maven test suite and frontend production build, and manually verified containerized PostgreSQL, Cassandra, Redis, Kafka, Gateway, and browser flows.

I never send production credentials, customer PII, payment data, proprietary logs, or unapproved source code to a public model. For a company environment, I use only approved enterprise tools and follow retention and data-classification policies. AI can reduce search and boilerplate time, but the engineer remains responsible for correctness, security, licensing, testing, and deployment approval.

### Concrete example

During this project, AI helped identify that stock cannot safely use Cassandra or Redis as transactional truth. I evaluated the suggestion, kept inventory in PostgreSQL, implemented a conditional update to prevent overselling, and used Outbox plus Kafka to update the Cassandra projection. The useful result was not “AI wrote the code”; it was that I made the failure boundary explicit and then verified the implementation.

## 14. What is the TPS for your service?

### Interview answer

I first clarify whether TPS means HTTP requests, committed business transactions, database transactions, or Kafka records. The platform capacity model is 200 average API requests per second and a 1,000-QPS peak. Checkout is designed for 50 peak orders per second. With four lines per normal order, that can create about 200 inventory reservation operations per second before retries, while one successful order creates one Order transaction.

These are sizing inputs, not measured production claims from this repository. A valid load-test result must include replicas, request mix, dataset, cache state, test duration, p95/p99 latency, error rate, CPU, GC, connection-pool wait, database latency, and consumer lag. Saying only “1,000 TPS” is incomplete because 1,000 cached item reads and 1,000 stock updates create completely different load.

For Kafka projection capacity, ordering is per item because Item Service uses `itemId` as the key. Six partitions allow at most six active consumers in one group in the local setup. I measure records consumed per second and lag growth; partition count alone does not prove throughput.

## 15. How did you handle security in your service?

### Interview answer

Auth Service stores BCrypt password hashes and issues signed JWTs containing the canonical user ID and role. API Gateway rejects missing, invalid, or expired bearer tokens before routing protected requests. Item Service also validates identity and authorization because it owns the rule that only sellers can create products and only the owning seller can update or delete an item. Order-to-Item stock calls use a separate internal-service token and receive the `ORDER_SERVICE` authority.

The services are stateless, CORS is restricted to the configured frontend origin, DTO validation runs at the API boundary, and PostgreSQL performs the final conditional inventory check. JWT claims are readable, so passwords, addresses, payment details, and secrets never belong in the token. Redis and Cassandra are performance stores and are never trusted to authorize stock or ownership changes.

### Production hardening discussion

For stronger production security, I would use asymmetric JWT signing with key rotation, validate issuer and audience, shorten access-token lifetime, rotate refresh tokens, store secrets in a managed secret store, use mTLS or workload identity for internal calls, and enforce least-privilege network and database policies. I would add rate limits for login and checkout, audit seller changes, scan dependencies/images, and test missing token, forged token, buyer write, cross-seller update, and replayed internal commands.

## 16. What are p95 and p99?

### Interview answer

P95 is the latency value at or below which 95% of requests completed; 5% were slower. P99 is the same boundary for 99%, leaving 1% slower. They describe tail latency better than an average. At 1,000 requests per second for one minute, there are 60,000 requests, so the slowest 1% represents about 600 user requests—p99 is not an edge case at that traffic level.

I report p50, p95, p99, throughput, and error rate together. A service can show a 20 ms average while a small percentage waits seconds on a connection pool or downstream timeout. In this project I would break latency down by Gateway route, Redis hit/miss, PostgreSQL query, Cassandra query, Feign dependency, Kafka publish, executor queue wait, and circuit-breaker state.

An SLO might say 99% of authenticated item reads complete below an agreed threshold while error rate remains below its limit. I do not invent the threshold before measuring representative production traffic and business expectations.

## 17. Give me one debug example and explain how you fixed it

### Interview answer

A buyer could log in successfully, but the shopping page displayed `Request failed (403)` when loading `GET /api/items`. I first used the browser network panel to verify the request path and Authorization header. Then I checked Gateway and Item Service separately: the JWT was valid and the request reached Item Service, so it was an authorization problem rather than routing, Eureka, or database failure.

The root cause was an overly broad security rule around `/api/items/**` that treated catalog reads like seller-management operations. I split authorization by HTTP method and route: authenticated buyers and sellers can read `/api/items` and `/api/items/{id}`, while create, update, delete, and `/mine` require `SELLER`; stock mutation requires `ORDER_SERVICE`. I rebuilt the service and verified that buyer GET returned 200, buyer POST returned 403, seller POST succeeded, and cross-seller update remained forbidden.

### Why this is a strong debugging example

The investigation narrowed the failure by layer instead of changing random code. A 403 proved that a component made an authorization decision; it was different from a 401 token failure, a 404 route mismatch, a 503 circuit breaker, or a 500 database exception. The lasting fix was a precise security rule plus negative tests, not simply permitting every request.

---

# Second interviewer

## 18. Why did you use Promise Cache, and why reduce eight calls to one?

### Interview answer

A Promise Cache, also called single-flight or request coalescing, temporarily stores an in-flight `CompletableFuture` for a key. If 100 requests miss Redis for the same hot item at the same moment, the first request starts one origin load and the other 99 wait on the same future instead of issuing 100 database calls. When the future completes, I remove the in-flight entry; successful data goes into the normal TTL cache, and failures are not cached as successful values.

Reducing eight calls to one is a different optimization: batching. An eight-line order contains eight different item IDs, so a Promise Cache cannot honestly turn those unique IDs into one request. A batch Item/Inventory API accepts all eight lines, validates them together, and returns one deterministic response. Eight network round trips become one, an exact 87.5% reduction in round trips. If the original workflow does eight item-detail calls plus eight reservation calls, one combined validation-and-reservation endpoint reduces sixteen calls to one, a 93.75% reduction.

### Project connection

The current `OrderItemLookupService` uses a bounded executor and `CompletableFuture` to overlap item-detail GETs. That changes elapsed waiting time but still performs one GET per item. Reservations are currently sequential to reduce partial concurrent mutation, but still perform one call per line. The stronger service contract is a batch reservation with a stable `reservationId`, all-or-none validation inside Item/Inventory Service, and an idempotent response.

### Promise Cache safety details

The in-flight map must be bounded or limited to known cache-miss paths. I remove entries in a completion handler, including exceptional completion, so failed futures do not poison the key. It reduces a cache stampede inside one service instance; Redis or another distributed mechanism is still needed across replicas, and PostgreSQL remains the source of truth.

## 19. How did you do logging in your service?

### Interview answer

The project uses SLF4J with Spring Boot's default Logback implementation. Item Service logs outbox publication failure with the event ID and exception so the event remains traceable while it is retried. The repository also contains a reusable AOP `LoggingAspect` for controller, service, and DAO method entry, exit, and exceptions, although the current root build does not yet package the `Common` module into every running service.

For a production service, I use structured JSON rather than free-form sentences. Every log includes timestamp, level, service, environment, instance, trace ID, span ID, operation, safe business identifier such as order ID or event ID, result, duration, and a bounded error category. I do not log passwords, JWTs, secrets, full addresses, payment data, or entire request objects.

I avoid debug logging for every request at normal production volume. Metrics answer “how often,” traces answer “where time was spent,” and logs explain a specific event. Logging must be asynchronous or carefully buffered where appropriate, sampled for high-volume success paths, and protected from unbounded exception payloads.

## 20. How do you add Grafana to Spring Boot?

### Interview answer

Grafana normally does not scrape Spring Boot directly. I add Micrometer's Prometheus registry to the service, expose `/actuator/prometheus`, configure Prometheus to scrape every service instance, and configure Grafana to query Prometheus as a data source. The project already includes Spring Boot Actuator but currently exposes only `health` and `info`, so Prometheus is the missing integration step.

### Maven dependency

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

### Spring configuration

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  metrics:
    tags:
      application: ${spring.application.name}
```

Prometheus then scrapes `http://<service>:<port>/actuator/prometheus`. Grafana dashboards graph request rate, errors, p95/p99 latency, JVM heap and GC, Hikari active/pending connections, executor queue depth, circuit-breaker state, Redis hit rate, Kafka producer errors and consumer lag, and business outcomes. Alerts should be based on sustained user impact or saturation rather than every individual exception.

I secure management endpoints with a separate management network/port or platform policy. I expose only necessary endpoints and never make environment variables or configuration dumps public.

## 21. How do you troubleshoot logs?

### Interview answer

I begin with the user-visible symptom, time range, environment, route, status code, and correlation or trace ID. I check metrics first to determine scope: one user, one Pod, one endpoint, or the whole service. Then I follow the trace across Gateway, Feign, database, Kafka, and downstream services and query structured logs by trace ID or business ID.

For an order failure, I inspect Gateway status and timeout, Order logs, Item reservation result, PostgreSQL transaction state, and compensation attempts. For an item projection problem, I correlate `eventId` and `itemId` across the outbox row, producer acknowledgement, Kafka partition/offset, consumer logs, Cassandra version, and Redis eviction. If the outbox is pending, publication is the problem; if Kafka lag grows, consumption is the problem; if offset advances but projection is stale, consumer logic or Cassandra write is the problem.

I do not stop at the first stack trace. I compare the first error with later retry noise, inspect deploy/config changes around the start time, and check resource saturation such as connection-pool wait, executor queue, memory/GC, disk, Kafka lag, and downstream latency. After recovery I record root cause, user impact, detection gap, corrective action, and a regression test or alert.

## 22. What are a Kafka consumer and partitions?

### Interview answer

A Kafka topic is an ordered log divided into partitions. A producer chooses a partition, often by hashing a message key. Ordering is guaranteed within one partition, not across the entire topic. A consumer reads records and tracks an offset, and consumers with the same group ID divide the topic's partitions so one partition is processed by at most one active member of that group at a time.

In Item Service, the topic is `item.events.v1`, the key is `itemId`, and the consumer group is `item-service-projection-v1`. Using `itemId` keeps all changes for one item in the same partition and therefore in partition order. The consumer builds the Cassandra projection and evicts Redis entries. Other responsibilities, such as Search or Analytics, use different consumer groups so each receives its own copy of every relevant event.

Local Compose sets a default of six partitions. Partition count controls maximum group parallelism and affects ordering, rebalancing, open files, broker work, and future key distribution. I select it from expected throughput, consumer processing rate, headroom, and hot-key behavior rather than using a large arbitrary number.

## 23. What happens with 20 partitions and 5 consumers?

### Interview answer

If all five consumers belong to the same consumer group and all 20 partitions are available, Kafka assigns approximately four partitions to each consumer. Each consumer processes its assigned partitions, and records within one partition retain their order. Five consumers can therefore work in parallel, but the group has partition-level capacity for as many as 20 active consumers.

If the group grows to 20 consumers, each can receive roughly one partition. If it grows to 25, five consumers are idle because a partition is not actively split between two consumers in the same group. If one of the five consumers fails, the group rebalances and the remaining four receive approximately five partitions each. During or after reassignment, processing must tolerate redelivery.

Different consumer groups are independent. Five projection consumers and five analytics consumers do not share the 20 partitions with each other; each group consumes the topic independently.

## 24. What happens if a message is consumed and the consumer then fails?

### Interview answer

The answer depends on when the offset was committed. If the consumer fails before committing the offset, Kafka assigns the partition to another consumer and the record is read again. If the consumer commits before the business side effect completes and then fails, Kafka considers the record processed and the side effect can be lost. If the database write succeeds but the offset is not committed before the crash, the record is delivered again and the side effect can happen twice unless the consumer is idempotent.

Item Service disables auto-commit and uses record acknowledgement mode. The listener throws on invalid or failed processing so the record is not treated as successful. Its Cassandra upsert compares aggregate version: an older or duplicate item event does not overwrite a newer projection. For money, inventory release, or one-time benefits, I also store an event ID or business idempotency key in a durable database with a unique constraint.

I design for at-least-once delivery: retry transient failures with bounded backoff, route poison records to a dead-letter topic after the retry policy, alert on lag and DLT growth, and provide a replay procedure. Kafka producer idempotence prevents certain duplicate writes from producer retries, but it does not make arbitrary consumer database side effects exactly once.

## 25. How would you design an address validation service architecture?

### Assumption

I interpret “address variation service” as an address **validation, normalization, and suggestion** service. Its job is to accept user-entered address text, normalize it, verify deliverability through internal rules or external providers, and return a canonical address plus confidence and alternatives. It does not silently overwrite the user's saved address.

### Interview answer

I would expose a synchronous `POST /api/addresses/validate` endpoint because Checkout needs an immediate answer, plus an optional asynchronous verification flow for slower enrichment. The service first validates input and country, normalizes casing and abbreviations, checks an exact-result cache, applies deterministic country-specific rules, and calls one or more provider adapters behind a timeout, circuit breaker, and bulkhead. It returns `VALID`, `CORRECTED`, `AMBIGUOUS`, `UNSUPPORTED`, or `UNAVAILABLE`, along with a canonical address, suggestions, confidence, provider version, and a validation ID.

```text
Client / Checkout
       │
       ▼
API Gateway → Address Validation API
                    │
                    ├── input validation and PII policy
                    ├── normalization strategy by country
                    ├── Redis cache by HMAC(normalized address)
                    ├── provider routing / circuit breaker
                    │       ├── postal provider adapter
                    │       └── secondary provider adapter
                    ├── PostgreSQL audit/result metadata
                    └── Kafka address-validated event
                              ├── Checkout/Order
                              ├── Fraud
                              └── Delivery planning
```

### Important design decisions

Country-specific normalization is a Strategy pattern, and provider integrations are Adapters. I use a stable request ID and idempotency key so a retry does not create duplicate audit events. Provider calls have a shorter timeout than the Checkout deadline; if all providers are unavailable, the API returns an honest `UNAVAILABLE` decision or allows a policy-controlled manual address path rather than claiming the address is valid.

Addresses are PII. Transport uses TLS, stored values are encrypted with managed keys, logs contain only validation ID and redacted fields, and access is audited. Cache keys should be a keyed hash of the normalized address rather than readable address text. Cache TTL depends on provider rules and address volatility, and the response records which rules/provider version produced the answer.

For scale, the API is stateless and horizontally replicated. Redis absorbs repeated validations, provider bulkheads protect the system from vendor slowness, and Kafka removes analytics or downstream enrichment from the response path. Metrics include validation rate, provider latency/error, correction rate, ambiguity rate, cache hit rate, fallback use, and Checkout abandonment after correction.

## 26. Do you know backpressure?

### Interview answer

Backpressure is how a slower consumer prevents a faster producer from creating an unbounded backlog in memory. In Reactive Streams, the subscriber signals demand with a bounded number of elements instead of accepting unlimited data. In ordinary service design, the same principle appears as bounded queues, connection pools, concurrency limits, rate limits, Kafka lag, and rejection policies.

The WebFlux Gateway uses Reactor's non-blocking pipeline and Reactive Streams demand. Order Service's named executor has at most 32 threads and a queue of 200; `CallerRunsPolicy` slows the submitting request when both are full instead of silently discarding work. That is a local form of backpressure, although request timeouts and downstream limits are still necessary.

For Kafka, the broker is the durable backlog. If Cassandra slows down, consumers should limit in-flight work, pause partitions when necessary, avoid committing unfinished records, and let lag grow visibly instead of copying unlimited messages into a JVM queue. Alerts on consumer lag and oldest-event age show whether the consumer can recover before the freshness SLO is violated.

## 27. What do you do if JDBC blocks in WebFlux? What happens if you remove it, and is there another solution?

### Interview answer

JDBC is blocking. If it runs on a Netty event-loop thread, that thread cannot serve other connections while waiting for the database, so a small number of slow queries can stall many unrelated requests. In this project the API Gateway is WebFlux and performs no JDBC work; database services use Spring MVC/JPA, where blocking JDBC is handled by a bounded request-thread and connection-pool model. That separation is deliberate.

If blocking JDBC must temporarily be called from a reactive service, I isolate it on a bounded scheduler:

```java
Mono.fromCallable(() -> repository.findById(id))
    .subscribeOn(Schedulers.boundedElastic());
```

This protects the event loop but does not make JDBC non-blocking. The scheduler and database pool must remain bounded, and the transaction must be contained within the worker execution. The cleaner choices are to keep persistence in an MVC/JPA service and call it with non-blocking `WebClient`, or use a genuinely reactive database driver such as R2DBC when its transaction and ecosystem tradeoffs are acceptable.

If the interviewer means “what happens if you remove `subscribeOn(boundedElastic())`,” the JDBC call executes on the current reactive thread and can block the event loop. If they mean “what happens if you remove `.block()`,” returning the `Mono` from the controller is correct because WebFlux subscribes and writes the result; calling a reactive method without returning, composing, or subscribing means the work may never execute. If they mean removing JDBC entirely, R2DBC or a separate blocking service is the architectural solution.

Adding more event-loop threads is not a real fix. It consumes more threads while retaining blocking behavior and can still exhaust database connections. I verify the solution with blocked-thread detection, event-loop utilization, connection-pool wait, p95/p99, and load tests under slow-database conditions.

## 28. What is the difference between Mono and Flux?

### Interview answer

`Mono<T>` represents an asynchronous result containing zero or one value. `Flux<T>` represents zero to many values over time. The distinction communicates cardinality: item-by-ID, login, or order creation naturally returns `Mono<Item>`, while a stream or list of item results can be represented as `Flux<Item>`.

Both are lazy publishers: the pipeline describes work and normally begins when subscribed. They support composition, error handling, cancellation, scheduling, and backpressure, although a single-value `Mono` has little element-by-element pressure to manage. `Flux` can honor downstream demand across many elements.

In this project, WebFlux is used at Gateway where filters return `Mono<Void>` because request processing completes once or fails. The downstream Item and Order services use MVC/JPA and return normal DTOs and lists. Wrapping a blocking JPA result in `Mono.just()` does not make the query reactive because the query already ran before the publisher was created.

## 29. How do you tune an API?

### Interview answer

I tune an API by measuring where time and capacity are spent, then removing work before adding threads or hardware. I begin with request rate, errors, p50/p95/p99, traces, CPU, allocation and GC, connection-pool wait, SQL time, cache hit rate, executor queue, downstream latency, Kafka lag, and payload size. The slowest dependency or most repeated operation determines the first change.

In this shopping platform, the main opportunities are batching chatty Item calls, removing N+1 inventory lookups, caching safe item and order reads, adding the correct PostgreSQL indexes, using pagination, keeping transactions short, moving non-critical side effects to Kafka, bounding concurrency, and setting coherent timeouts and circuit breakers.

### Project examples with defensible arithmetic

An eight-line checkout currently performs eight item-detail calls and eight stock-reservation calls. One batch read plus one batch reservation reduces sixteen network calls to two, an 87.5% reduction; one combined validation-and-reservation call reduces them to one, a 93.75% reduction. This proves round-trip reduction, not a specific latency improvement.

A 100-item page that performs one inventory lookup per item creates 100 extra queries. One batch `WHERE item_id IN (...)` query reduces those 100 round trips to one, a 99% reduction. Pagination prevents unbounded result sets, and an index beginning with `user_id` supports seller-store queries without scanning all items.

At a modeled 700 catalog reads per second and an 85% Redis hit rate, only `700 × 0.15 = 105` origin reads per second remain, an 85% reduction. If Redis fails, origin traffic can jump from 105 back to 700, or 6.67 times, so the design also needs origin concurrency limits, timeouts, and load shedding. Cached stock is never used to approve checkout.

### Tuning sequence

1. Define the endpoint SLO and reproduce the real request mix with warm and cold caches.
2. Use traces and query metrics to identify network chattiness, N+1 queries, scans, locks, serialization, or queue wait.
3. Make one change—batching, index, projection, cache, payload reduction, or concurrency limit—and rerun the same test.
4. Compare throughput, p95/p99, error rate, CPU, GC, database load, and downstream impact.
5. Run a longer soak and failure scenario because a short happy-path test misses leaks, cache-loss amplification, and backlog growth.

I never call higher QPS a success if errors, tail latency, duplicate orders, overselling, or downstream saturation became worse.

---

# Rapid follow-up questions

## Why not use Redis inventory as the source of truth?

Redis is excellent for fast reads and admission control, but a cache can be evicted, expire, or fail over. PostgreSQL uses an atomic conditional update so concurrent buyers cannot spend the same final unit. Redis may reduce traffic before the database, but PostgreSQL makes the final stock decision.

## Why use PostgreSQL and Cassandra together?

PostgreSQL owns transactional Item, Inventory, and Outbox rows. Cassandra stores denormalized query projections optimized for high-volume reads. If Cassandra is down, the write is not lost; the Outbox/Kafka backlog updates the projection after recovery. This is a CQRS-style separation between authoritative writes and rebuildable reads.

## What does the transactional Outbox solve?

It prevents the database/Kafka dual-write gap. Item, Inventory, and Outbox commit in one PostgreSQL transaction. A publisher sends committed outbox rows later. A crash after database commit does not lose publication intent, and duplicate delivery is safe because the projection checks item version.

## Why use a circuit breaker as well as a timeout?

A timeout limits one call. A circuit breaker uses recent results to decide whether new calls should be attempted. When a dependency is consistently slow, failing every request only after three seconds can consume thousands of in-flight slots. An open breaker fails locally with 503 and gives the dependency time to recover.

## What would you monitor first during an incident?

I start with user-facing request rate, error rate, and p95/p99. Then I check saturation: active requests, Hikari wait, executor queue, Redis hit-rate collapse, Kafka lag, outbox oldest age, database locks, and downstream timeouts. Low CPU does not prove health; every thread can be waiting on a blocked dependency.

---

# Official references

- [Spring Boot metrics and Prometheus](https://docs.spring.io/spring-boot/reference/actuator/metrics.html)
- [Spring WebFlux reference](https://docs.spring.io/spring/reference/web/webflux.html)
- [Reactor Core reference: Mono, Flux, and backpressure](https://projectreactor.io/docs/core/release/reference/)
- [Spring profiles and environment abstraction](https://docs.spring.io/spring-framework/reference/core/beans/environment.html)
- [Apache Kafka documentation](https://kafka.apache.org/documentation/)
- [IntelliJ IDEA Spring Boot run configuration](https://www.jetbrains.com/help/idea/run-debug-configuration-spring-boot.html)
- [WalmartLabs Concord deployment training](https://github.com/walmartlabs/concord-website/blob/master/training/includes/concord-1-beginner-6-cicd.md)
