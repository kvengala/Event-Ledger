# Event Ledger

A financial transaction **Event Ledger** system built as two Spring Boot microservices. It ingests transaction events from upstream systems, handles duplicate delivery and out-of-order arrival, and keeps account balances correct.

## Architecture

```
Browser / Client ──→ Event Gateway (8080) ──REST──→ Account Service (8081)
                           │                              │
                      H2 (events)                   H2 (accounts)
```

| Service | Port | Role |
|---------|------|------|
| **event-gateway** | 8080 | Public API — validate events, enforce idempotency, persist events, call Account Service |
| **account-service** | 8081 | Internal — apply transactions, compute balances, account queries |

Each service runs in its own process with its own embedded H2 database. They do not share storage.

### Request flow

1. Client `POST /events` to Gateway
2. Gateway checks idempotency (`eventId` unique)
3. Gateway calls Account Service to apply the transaction (with retry + timeout)
4. On success, Gateway persists the event and returns `201 Created`
5. Duplicate submissions return `200 OK` with the original event

## Prerequisites

- **JDK 21**
- **Maven 3.9+** (or use `mvnw.cmd` on Windows after downloading the wrapper JAR)
- **Docker & Docker Compose** (optional, for containerized run)

## Build

From the repository root:

```bash
mvn clean package
```

## Run locally (manual)

Start Account Service first, then Gateway:

```bash
# Terminal 1
cd account-service
mvn spring-boot:run

# Terminal 2
cd event-gateway
mvn spring-boot:run
```

Gateway defaults to `http://localhost:8080`. Account Service defaults to `http://localhost:8081`.

## Run with Docker Compose

From the repository root:

```bash
docker compose up --build
```

- Gateway: http://localhost:8080  
- Account Service: http://localhost:8081 (internal to compose network; exposed for debugging)

> **Note:** Both services use in-memory H2 databases. Data is lost when a container or process restarts.

## Run tests

```bash
mvn test
```

Tests include unit tests, WireMock resiliency tests, and end-to-end integration tests that start both services in-process.

## API reference

### Event Gateway (public)

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/events` | Submit a transaction event |
| `GET` | `/events/{id}` | Get event by UUID or `eventId` |
| `GET` | `/events?account={accountId}` | List events for an account (ordered by `eventTimestamp` ASC) |
| `GET` | `/health` | Health check (includes DB status) |
| `GET` | `/metrics` | Micrometer metrics |

**Example event payload:**

```json
{
  "eventId": "evt-001",
  "accountId": "acct-123",
  "type": "CREDIT",
  "amount": 150.00,
  "currency": "USD",
  "eventTimestamp": "2026-05-15T14:02:11Z",
  "metadata": {
    "source": "mainframe-batch",
    "batchId": "B-9042"
  }
}
```

### Account Service (internal)

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/accounts/{accountId}/transactions` | Apply a transaction |
| `GET` | `/accounts/{accountId}/balance` | Current balance |
| `GET` | `/accounts/{accountId}` | Account details + recent transactions |
| `GET` | `/health` | Health check |

## Example usage

Submit an event:

```bash
curl -s -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -H "X-Trace-Id: demo-trace-001" \
  -d '{
    "eventId": "evt-001",
    "accountId": "acct-123",
    "type": "CREDIT",
    "amount": 150.00,
    "currency": "USD",
    "eventTimestamp": "2026-05-15T14:02:11Z"
  }'
```

Check balance (via Account Service):

```bash
curl -s http://localhost:8081/accounts/acct-123/balance
```

List events for an account:

```bash
curl -s "http://localhost:8080/events?account=acct-123"
```

Health checks:

```bash
curl -s http://localhost:8080/health
curl -s http://localhost:8081/health
```

## Core behavior

| Requirement | Implementation |
|-------------|----------------|
| **Idempotency** | Unique `eventId` in both services; duplicates return original event without side effects |
| **Out-of-order events** | Balance = sum(CREDIT) − sum(DEBIT); listings sorted by `eventTimestamp` |
| **Validation** | Required fields, positive amount, CREDIT/DEBIT only → `400 Bad Request` |
| **Graceful degradation** | `POST /events` → `503` when Account Service is down; `GET /events` still works from Gateway DB |

## Resiliency

The Gateway uses **timeout + retry with exponential backoff** (Resilience4j) when calling Account Service:

- **3 attempts** with 200ms initial wait, 2× backoff multiplier
- **2s** HTTP connect/read timeout on RestClient
- Retries on server errors and network faults; **no retry** on client (4xx) errors
- After retries are exhausted → `503 Service Unavailable`; event is **not** persisted

**Why this pattern?** Retries absorb transient failures; exponential backoff avoids overwhelming a recovering service; timeouts prevent hung threads; skipping 4xx retries avoids repeating validation failures.

## Observability

### Structured logging

Both services emit JSON logs via Logstash encoder:

```json
{
  "@timestamp": "2026-06-15T12:00:00.000Z",
  "level": "INFO",
  "serviceName": "event-gateway",
  "traceId": "demo-trace-001",
  "message": "Applied transaction evt-001 to account acct-123"
}
```

### Distributed tracing

- Gateway generates or accepts `X-Trace-Id` on each request
- Trace ID is propagated to Account Service on outbound calls
- Response includes `X-Trace-Id` header

### Metrics

Custom counter: `events.submitted.total` tagged by `endpoint` and `status` (`created`, `duplicate`, `unavailable`).

```bash
curl -s "http://localhost:8080/metrics/events.submitted.total?tag=status:created"
```

## Project structure

```
event-ledger/
├── pom.xml                 # Parent POM (Java 21, Spring Boot 3.3)
├── docker-compose.yml
├── account-service/        # Balance & transaction service
└── event-gateway/          # Public event API
```

## Technology stack

- Java 21, Spring Boot 3.3, Maven
- H2 (embedded, in-memory per service)
- Resilience4j (retry + timeout)
- Micrometer / Spring Actuator
- Logstash Logback Encoder (JSON logs)
