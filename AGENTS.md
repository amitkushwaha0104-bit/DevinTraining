# AGENTS.md - project rules for AI/dev agents

## Technology

Java 21, Spring Boot 3.5.x, Spring AI 1.1.x (Anthropic Claude), Spring MVC + Thymeleaf,
Spring Data JPA, Spring Security, MySQL 8, Flyway, Maven, JUnit 5 + Mockito, Testcontainers.

## Verify before done

```bash
mvn -q verify
```

All tests must pass. `MySqlFlywayTests` self-skips without Docker.

## Hard rules

- **Never** add tools or code paths that execute financial, customer-facing or
  account-control actions (no card block, refund, transfer, report, customer contact).
- Every recommendation must pass `RecommendationValidator`; `humanReviewRequired` is
  always true. Raw model output is never persisted as an approved recommendation.
- Masked references only: reject any string containing 12+ consecutive digits
  (`MaskingRules`). No real customer data, credentials or secrets may be committed.
- The audit trail is append-only. Do not add update/delete paths to `AuditEvent`.
- Keep the `FraudCaseProcessor` contract framework-neutral; Spring AI types must not leak
  into `domain/`, `application/`, `api/` or `persistence/`.
- State transitions only through `FraudCaseService.transition` - never from model output.
- Tool fixtures must stay deterministic (stable hash of masked refs + config seed).
- Policy retrieval must abstain (empty result) below the similarity threshold; never
  fabricate passages or citations.
- Conversation memory is keyed by `caseId` and must never be shared across cases.
- Do not log raw prompts/responses or sensitive payloads; use hashes and summaries.

## Config

Secrets come from env vars only (`ANTHROPIC_API_KEY`, `DB_*`). `application-example.yml`
and `.env.example` hold placeholders only. Default `capstone.processor-mode=rules` keeps
the app runnable with no model dependency.
