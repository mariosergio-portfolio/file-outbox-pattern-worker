# job-outbox

Transactional Outbox watcher microservice for the **file manager** platform. It guarantees at-least-once delivery of import and export events by periodically scanning the outbox tables for stale `PENDING` records and either re-publishing them to RabbitMQ or, in contingency mode, invoking the downstream service directly over HTTP.

---

## Table of Contents

1. [Architectural Role](#1-architectural-role)
2. [Transactional Outbox Pattern](#2-transactional-outbox-pattern)
3. [Module Structure](#3-module-structure)
4. [Domain Model](#4-domain-model)
5. [Scheduled Watcher Jobs](#5-scheduled-watcher-jobs)
6. [Messaging Layer](#6-messaging-layer)
7. [Contingency Mode](#7-contingency-mode)
8. [Configuration Reference](#8-configuration-reference)
9. [Infrastructure Dependencies](#9-infrastructure-dependencies)
10. [Technology Stack](#10-technology-stack)

---

## 1. Architectural Role

```
┌──────────────────────┐       writes outbox row       ┌────────────────────┐
│  file-manager-api    │ ─────────────────────────────► │  OUTBOX_IMPORT     │
│  (import trigger)    │                                │  OUTBOX_EXPORT     │
└──────────────────────┘                                └────────┬───────────┘
                                                                 │ polls every N seconds
                                                        ┌────────▼───────────┐
                                                        │    job-outbox       │
                                                        │  (this service)     │
                                                        └────────┬───────────┘
                                          ┌──────────────────────┼──────────────────────┐
                                          │ normal mode           │ contingency mode     │
                                   ┌──────▼──────┐        ┌──────▼───────┐
                                   │  RabbitMQ   │        │ processor-   │
                                   │  (import /  │        │ service      │
                                   │   export    │        │ (HTTP)       │
                                   │   queues)   │        └──────────────┘
                                   └─────────────┘
```

`job-outbox` is a **reliability sidecar**: it does not originate events — it ensures that events written by other services to the shared outbox tables are eventually delivered, even if RabbitMQ was temporarily unavailable at the time of the original write.

---

## 2. Transactional Outbox Pattern

The pattern solves the *dual-write problem*: a service must atomically update its own database **and** publish a message. Sending directly to RabbitMQ inside a transaction is unsafe because the message may be sent before the transaction commits (or not sent if the broker is down).

**Solution:**

1. The originating service (e.g. `file-manager-api`) writes a row to `OUTBOX_IMPORT` or `OUTBOX_EXPORT` inside the same database transaction that creates the main record. Row state starts as `PENDING`.
2. `job-outbox` polls the outbox tables on a fixed schedule.
3. For each `PENDING` row older than the configured stale threshold, it deserialises the JSON payload and forwards it — either to RabbitMQ (normal mode) or directly to `processor-service` (contingency mode).
4. On success the row transitions to `PROCESSING` and `processedAt` is stamped.

This guarantees **at-least-once delivery** without any distributed transaction or two-phase commit.

---

## 3. Module Structure

```
job-outbox/
├── src/main/java/com/planet/joboutbox/
│   ├── JobOutboxApplication.java          # Spring Boot entry point (@EnableScheduling)
│   ├── config/
│   │   ├── JacksonConfig.java             # ObjectMapper customisation
│   │   ├── OutboxProperties.java          # Typed config (prefix: outbox)
│   │   ├── RabbitMQConfig.java            # Exchange / queue / binding declarations
│   │   └── RestClientConfig.java          # RestClient wired to processor-service URL
│   ├── domain/
│   │   ├── OutboxImport.java              # JPA entity — OUTBOX_IMPORT table
│   │   ├── OutboxExport.java              # JPA entity — OUTBOX_EXPORT table
│   │   ├── ImportState.java               # Enum: PENDING | PROCESSING | COMPLETED | ERROR
│   │   ├── ExportState.java               # Enum: PENDING | PROCESSING | COMPLETED | ERROR
│   │   ├── FileDomain.java                # Enum: CUSTOMER | PRODUCT | EMPLOYEE
│   │   └── ExportFormat.java             # Enum: CSV | TXT | XLSX
│   ├── messaging/
│   │   ├── ImportEvent.java               # Record (importId, state, s3Key, companyId, domain)
│   │   ├── ExportEvent.java               # Record (exportId, state, companyId, domain, format, columns, customerEmail)
│   │   ├── ImportEventPublisher.java      # Sends ImportEvent to import.exchange
│   │   └── ExportEventPublisher.java      # Sends ExportEvent to export.exchange
│   ├── repository/
│   │   ├── OutboxImportRepository.java    # findStaleByState(state, staleBefore)
│   │   └── OutboxExportRepository.java    # findStaleByState(state, staleBefore)
│   └── service/
│       ├── OutboxImportWatcherJob.java    # @Scheduled — scans OUTBOX_IMPORT
│       ├── OutboxExportWatcherJob.java    # @Scheduled — scans OUTBOX_EXPORT
│       ├── ContingencyProcessorClient.java # HTTP POST /internal/force-process
│       └── ContingencyExportClient.java   # HTTP POST /internal/force-export
└── src/main/resources/
    ├── application.yml
    └── db/migration/                      # Flyway scripts
```

---

## 4. Domain Model

### Outbox Entities

Both outbox tables share the same lifecycle. The difference is that `OutboxImport` additionally tracks `updatedAt` via `@PreUpdate`.

| Column                      | Type        | Description                                       |
|-----------------------------|-------------|---------------------------------------------------|
| `outbox_*_id`               | BIGINT (PK) | Auto-generated surrogate key                      |
| `import_id` / `export_id`   | UUID        | Business identifier from the originating event    |
| `payload`                   | TEXT        | Full JSON serialisation of the event              |
| `state`                     | VARCHAR(20) | `PENDING` → `PROCESSING` → `COMPLETED` / `ERROR`  |
| `created_at`                | TIMESTAMP   | Set at creation; used to detect stale records     |
| `processed_at`              | TIMESTAMP   | Stamped when the watcher picks up the record      |

### State Machine

```
 [write by originator]
         │
         ▼
      PENDING  ──── stale threshold exceeded ────► PROCESSING
                                                       │
                               ┌───────────────────────┤
                               │                       │
                           COMPLETED                 ERROR
                    (set by downstream service)  (on exception)
```

> `job-outbox` only advances records to `PROCESSING`. The final state (`COMPLETED` or `ERROR`) is written back by the downstream service that consumes the event.

---

## 5. Scheduled Watcher Jobs

Both `OutboxImportWatcherJob` and `OutboxExportWatcherJob` follow the same algorithm:

```
every outbox.schedule-rate-ms (default: 60 000 ms)
│
├─ compute staleBefore = now() − outbox.stale-threshold-minutes (default: 5 min)
├─ SELECT * FROM OUTBOX_* WHERE state = 'PENDING' AND created_at <= staleBefore
│
├─ for each record:
│   ├─ deserialise payload → ImportEvent / ExportEvent
│   ├─ if contingency-mode = false  →  publish to RabbitMQ exchange
│   │  if contingency-mode = true   →  HTTP POST to processor-service
│   ├─ set state = PROCESSING
│   ├─ set processedAt = now()
│   └─ save record (transactional)
│
└─ log outcome; swallow per-record exceptions to protect the batch
```

Key design decisions:

- **`@Transactional` on `watch()`** — the state update and the save are atomic; if the broker/HTTP call succeeds but the DB write fails, the record remains `PENDING` and will be retried on the next tick (acceptable for at-least-once semantics).
- **Per-record exception handling** — a single bad payload does not abort the entire batch.
- **Configurable rate and threshold** — both are externalised as environment variables so they can be tuned per environment without a code change.

---

## 6. Messaging Layer

### Exchanges and Queues

| Logical name    | Exchange          | Queue          | Routing key          |
|-----------------|-------------------|----------------|----------------------|
| Import pipeline | `import.exchange` | `import.queue` | `import.routing.key` |
| Export pipeline | `export.exchange` | `export.queue` | `export.routing.key` |

Both exchanges are `DirectExchange` (durable, non-auto-delete). Queues are durable. The `Jackson2JsonMessageConverter` is used for all serialisation.

### Event Records

**`ImportEvent`**
```
importId      UUID        — correlation ID
state         String      — current state string
s3Key         String      — S3 object path of the uploaded CSV
companyId     Long        — tenant identifier
domain        FileDomain  — CUSTOMER | PRODUCT | EMPLOYEE
```

**`ExportEvent`**
```
exportId      UUID          — correlation ID
state         String        — current state string
companyId     Long          — tenant identifier
domain        FileDomain    — CUSTOMER | PRODUCT | EMPLOYEE
format        ExportFormat  — CSV | TXT | XLSX
columns       List<String>  — requested column names
customerEmail String        — recipient e-mail for the export result
```

---

## 7. Contingency Mode

When RabbitMQ is unavailable or degraded, the service can be switched to **contingency mode** without redeployment:

```yaml
outbox:
  contingency-mode: true   # or env var OUTBOX_CONTINGENCY_MODE=true
```

In this mode the watcher jobs bypass RabbitMQ entirely and invoke `processor-service` synchronously over HTTP:

| Flow   | Normal mode endpoint       | Contingency endpoint            |
|--------|----------------------------|---------------------------------|
| Import | RabbitMQ `import.exchange` | `POST /internal/force-process`  |
| Export | RabbitMQ `export.exchange` | `POST /internal/force-export`   |

The `RestClient` base URL is configured via `outbox.processor-service-url` (default: `http://localhost:8081`).

> Contingency mode trades RabbitMQ's durability and backpressure guarantees for synchronous reliability. Use it only as a temporary fallback.

---

## 8. Configuration Reference

All properties can be overridden via environment variables (shown in parentheses).

| Property                          | Env var                          | Default                   | Description                                                      |
|-----------------------------------|----------------------------------|---------------------------|------------------------------------------------------------------|
| `outbox.stale-threshold-minutes`  | `OUTBOX_STALE_THRESHOLD_MINUTES` | `5`                       | Age in minutes before a `PENDING` record is considered stale    |
| `outbox.schedule-rate-ms`         | `OUTBOX_SCHEDULE_RATE_MS`        | `60000`                   | Watcher poll interval in milliseconds                            |
| `outbox.contingency-mode`         | `OUTBOX_CONTINGENCY_MODE`        | `false`                   | Bypass RabbitMQ and call processor-service directly              |
| `outbox.processor-service-url`    | `PROCESSOR_SERVICE_URL`          | `http://localhost:8081`   | Base URL for contingency HTTP calls                              |
| `spring.datasource.url`           | `DB_HOST`, `DB_PORT`, `DB_NAME`  | `localhost:5432/planet`   | PostgreSQL connection                                            |
| `spring.rabbitmq.host`            | `RABBITMQ_HOST`                  | `localhost`               | RabbitMQ broker host                                             |
| `spring.rabbitmq.port`            | `RABBITMQ_PORT`                  | `5672`                    | RabbitMQ broker port                                             |
| `spring.rabbitmq.username`        | `RABBITMQ_USER`                  | `guest`                   | RabbitMQ username                                                |
| `spring.rabbitmq.password`        | `RABBITMQ_PASSWORD`              | `guest`                   | RabbitMQ password                                                |

---

## 9. Infrastructure Dependencies

| Dependency        | Purpose                                                   | Required              |
|-------------------|-----------------------------------------------------------|-----------------------|
| PostgreSQL        | Persistence for `OUTBOX_IMPORT` and `OUTBOX_EXPORT` tables | Always               |
| RabbitMQ          | Message broker for import and export event queues         | Normal mode only      |
| processor-service | Downstream processor called in contingency mode           | Contingency mode only |
| Flyway            | Database schema migration (`db/migration/`)               | Always                |

---

## 10. Technology Stack

| Technology                  | Version | Role                                  |
|-----------------------------|---------|---------------------------------------|
| Java                        | 25      | Runtime                               |
| Spring Boot                 | 4.0.0   | Application framework                 |
| Spring Scheduling           | —       | `@Scheduled` watcher jobs             |
| Spring Data JPA / Hibernate | —       | Outbox entity persistence             |
| Spring AMQP / RabbitMQ      | —       | Event publishing                      |
| Spring Web (`RestClient`)   | —       | Contingency HTTP client               |
| PostgreSQL                  | —       | Outbox persistence store              |
| Flyway                      | —       | Schema migrations                     |
| Lombok                      | —       | Boilerplate reduction                 |
| Jackson                     | —       | JSON serialisation of event payloads  |
