# Intelligent Payment Fraud Investigation & Dispute Resolution Agent

Capstone project: an explainable, agentic assistant that collects fraud evidence, retrieves
applicable policy, recommends a case route and keeps the **final disposition with a human
reviewer**. All data is synthetic; no real bank connectivity exists.

## Safety boundary

The assistant **recommends** a route, priority or evidence request. It can never:
- block a card, issue a refund or credit, move funds
- file a report or contact a customer
- bypass `humanReviewRequired` - every recommendation is advisory

## Architecture

```
src/main/java/com/capstone/fraud/
  api/             REST controllers, DTOs, masking rules          -> Experience layer
  web/             Thymeleaf UI controllers and forms
  application/     FraudCaseService, InvestigationOrchestrator    -> Application layer
  domain/          enums + state machine (framework-neutral)      -> Domain layer
  agent/common/    FraudCaseProcessor contract + validator        -> shared contract
  agent/rules/     RulesFraudCaseAgent (deterministic fallback)
  agent/springai/  Spring AI adapter + JPA case memory
  tools/           EvidenceTools (8 allow-listed tools), fixtures, ToolRunner
  knowledge/       policy ingestion, hashing embeddings, retriever
  persistence/     JPA entities, repositories, append-only AuditWriter
  config/          security + capstone properties
src/main/resources/
  prompts/         fraud-system.txt, fraud-user.txt
  policies/        synthetic policy documents (markdown + front matter)
  templates/       Thymeleaf screens
  db/migration/    Flyway V1__init.sql
```

Key design decision: **evidence collection is deterministic and happens in application code**
(the orchestrator runs allow-listed tools through `ToolRunner`); the model only *interprets*
validated evidence. This keeps tool permissions strictly in application code and makes the
rules path and AI path exercise identical invariants.

### Modes (`capstone.processor-mode`)

| Mode | Behaviour |
|---|---|
| `rules` (default) | Deterministic rules engine R-01..R-06; no model dependency |
| `spring-ai` | Claude via Spring AI Anthropic; falls back to rules on model failure |

## Prerequisites

- Java 21, Maven 3.8+
- MySQL 8 running locally **or** Docker (for `docker compose up` / Testcontainers tests)
- `ANTHROPIC_API_KEY` only for `spring-ai` mode

## Build & test

```bash
mvn clean verify        # unit + H2 integration tests; Testcontainers test needs Docker
```

## Run

**Rules mode (no API key needed):**

```bash
sudo mysql -e "CREATE DATABASE IF NOT EXISTS fraudcapstone; CREATE USER IF NOT EXISTS 'fraud'@'%' IDENTIFIED BY 'fraud'; GRANT ALL ON fraudcapstone.* TO 'fraud'@'%'; FLUSH PRIVILEGES;"
mvn spring-boot:run
```

**AI mode:**

```bash
export ANTHROPIC_API_KEY=sk-ant-...
export PROCESSOR_MODE=spring-ai
mvn spring-boot:run
```

**Docker:**

```bash
docker compose up --build   # starts MySQL 8 + app on :8080
```

UI: http://localhost:8080 — demo users `agent`/`agent123`, `investigator`/`invest123`,
`reviewer`/`review123`, `auditor`/`audit123`, `lead`/`review123` (synthetic; override via env).

## API

| Method | Endpoint | Role | Purpose |
|---|---|---|---|
| POST | `/api/cases` | AGENT/INVESTIGATOR | create + validate a fraud case |
| GET | `/api/cases/{id}` | any | case summary + state |
| POST | `/api/cases/{id}/investigations` | INVESTIGATOR | run bounded investigation |
| GET | `/api/cases/{id}/evidence` | any | evidence + provenance |
| GET | `/api/cases/{id}/tool-traces` | any | redacted tool trace |
| GET | `/api/cases/{id}/recommendation` | any | latest validated recommendation |
| GET | `/api/cases/{id}/citations` | any | policy citation metadata |
| POST | `/api/cases/{id}/messages` | INVESTIGATOR | case-scoped follow-up |
| POST | `/api/cases/{id}/reviews` | REVIEWER | record disposition + rationale |

### Sample request

```bash
curl -u agent:agent123 -H 'Content-Type: application/json' -X POST localhost:8080/api/cases -d '{
  "maskedCustomerRef":"CUST-••••-4821","transactionRef":"TXN-2026-784102","amount":48750.00,
  "currency":"INR","occurredAt":"2026-11-06T08:42:00Z","status":"POSTED","channel":"CARD_NOT_PRESENT",
  "maskedMerchant":"NORTHSTAR DIGITAL ••••73","issueType":"MERCHANT_NOT_RECOGNISED",
  "customerStatement":"I do not recognise this purchase and still have my card.",
  "cardPossession":true,"merchantRecognised":false,"syntheticDataConfirmed":true,"reviewRequired":true}'
```

### Named demo refs

| transactionRef | Fixture profile |
|---|---|
| `TXN-2026-784102` | unauthorised fraud (new device + location anomaly) |
| `TXN-2026-DUP01` | confirmed duplicate fingerprint |
| `TXN-2026-MRC01` | recognised merchant dispute |
| `TXN-2026-MISSING` | transaction record not found |

All other refs produce deterministic hash-derived evidence (same input → same output).

## Tests

- **Unit**: masking rules, validator contract, rules engine scenarios, fixture determinism
- **Tool contract**: provenance, determinism, structured not-found
- **RAG**: relevant retrieval with citation metadata, abstention, withdrawn-policy filtering
- **Integration (H2 + MockMvc)**: intake→investigate→recommend→review, role boundaries, T04/T08/T12
- **Memory isolation (T10)**: case A facts never visible to case B
- **Testcontainers MySQL**: `MySqlFlywayTests` runs only when Docker is available

## Limitations

- Embedding model is a deterministic hashing vectorizer (offline, reproducible); swap for a
  real embedding provider before any sandboxed evaluation.
- SimpleVectorStore is in-memory; policy index rebuilds on startup.
- Demo credentials are for local use only.
