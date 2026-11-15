# Capstone Self-Evaluation Report

**Participant:** Amit Raj (amit.raj7@wipro.com)
**Project:** Intelligent Payment Fraud Investigation and Dispute Resolution Agent
**Evaluator:** Devin — acting as senior Java AI solution architect, BFS fraud-domain reviewer, secure-code reviewer, and test architect
**Date:** 2026-11-15

## 1. Executive Summary
- Project: `com.capstone:fraud-investigation-agent` 1.0.0 — synthetic payment-fraud case investigation with bounded evidence tools, policy-grounded recommendations, and mandatory human disposition
- Primary framework: Spring AI 1.1.8 (Anthropic Claude)
- Secondary framework: None (LangChain4j not implemented — permitted by the rubric's framework scoring rule)
- Build status: `mvn -B verify` → **BUILD SUCCESS** (verified 2026-11-15, ~67 s)
- Test status: **42 tests run — 42 passed, 0 failed, 0 skipped, 0 errors** (includes a real Testcontainers MySQL 8 + Flyway execution)
- Overall score: **86/100**
- Percentage: **86%**
- Readiness: **Demo Ready**
- Scoring model used: **Spring-AI-only model** — metric 5 (LangChain4j) marked Optional Not Implemented; its 10 points redistributed to metric 4 (Spring AI evaluated out of 20). Total remains 100.

## 2. Repository and Technology Overview

| Area | Finding | Evidence |
|---|---|---|
| Business objective | Payment fraud/dispute triage assistant; advisory recommendations only, final disposition with human reviewer | `README.md`, `AGENTS.md` |
| Java / Spring Boot | Java 21, Spring Boot 3.5.16 | `pom.xml` (`<java.version>21</java.version>`, parent `3.5.16`) |
| Build tool / deps | Maven; Spring Web, Thymeleaf+extras, Data JPA, Security, Validation, Actuator, Flyway(+mysql), MySQL driver, H2, Lombok, Testcontainers, Spring AI Anthropic + vector-store | `pom.xml` |
| AI framework | Spring AI only | `spring-ai-starter-model-anthropic`, `SpringAiFraudCaseAgent.java` |
| Model provider | Anthropic, `claude-sonnet-4-5`, temperature 0.0 — all env-driven | `application.yml` lines 15–22 |
| Database / persistence | MySQL 8 + Flyway `V1__init.sql`, JPA `ddl-auto=validate`; 9 entities, 9 repositories | `src/main/resources/db/migration/V1__init.sql`, `persistence/` |
| Vector store | `SimpleVectorStore` (in-memory) + deterministic `HashingEmbeddingModel` (512-dim hashing embedder) | `knowledge/PolicyIngestionService.java`, `knowledge/HashingEmbeddingModel.java` |
| UI | Thymeleaf + Bootstrap 5.3.3, 9 templates, Spring Security extras | `templates/`, `web/CaseUiController.java` |
| MCP / external integration | None — tools are local `@Tool` methods; no MCP server configured | `tools/EvidenceTools.java` |
| Entry point | `FraudCaseAssistantApplication` | `src/main/java/com/capstone/fraud/FraudCaseAssistantApplication.java` |
| Framework-neutral contract | `FraudCaseProcessor`, `FraudCaseContext`, `FraudCaseRecommendation`, `ValidatedRecommendation`, `RecommendationValidator` | `agent/common/` |
| Config modes | `capstone.processor-mode`: `rules` (default, offline) / `spring-ai` | `application.yml` line 28; `@ConditionalOnProperty` on `SpringAiFraudCaseAgent` |

## 3. Build and Test Results

| Command | Result | Details |
|---|---|---|
| `mvn -B verify` | **SUCCESS** | 42 tests, 0 failures/errors/skips; jar repackaged. Verified twice (2026-11-15) |
| `mvn test` (surefire) | SUCCESS | 10 test classes, 42 tests |
| `MySqlFlywayTests` | SUCCESS | Real MySQL 8 container via Testcontainers (Docker 29.1.3); Flyway V1 applied + Hibernate `validate` passes — not skipped |
| Live smoke test (rules mode) | PASS | Full lifecycle on real MySQL at :8090 — see §6 |
| JaCoCo / coverage | Not configured | No coverage plugin in `pom.xml` — coverage % unavailable |
| Compile warnings | None blocking | Mockito self-attach + dynamic-agent JDK warnings only |
| Credential-dependent failures | None | All tests offline-deterministic; `rules` mode needs no `ANTHROPIC_API_KEY` |

## 4. Evaluation Scorecard

| # | Metric | Maximum | Score | Status | Evaluator Comments | Evidence |
|---|---|---:|---:|---|---|---|
| 1 | BFS fraud & dispute use case | 10 | **10** | Implemented | Full intake→lifecycle→disposition; masked synthetic refs; 6 classifications incl. all required; fraud/dispute/payment-ops routes separated; zero loan-underwriting terms (grep-verified) | `CaseIntakeRequest`, `domain/Classification`, `CaseStatus` |
| 2 | Architecture & maintainability | 12 | **11** | Implemented | Clean layered packages; `FraudCaseProcessor` contract framework-neutral; single shared `RecommendationValidator` serves both adapters (no duplicated rules); config-driven mode. Minor: agent resolved via `ApplicationContext` holder rather than pure DI | package layout, `InvestigationOrchestrator.SpringAiAgentHolder` |
| 3 | Investigation workflow & tools | 15 | **12** | Implemented (gaps) | 8 `@Tool` methods with `@ToolParam` schemas; deterministic seeded fixtures; `ToolRunner` bounded execution (2500 ms → TIMEOUT/FAILED/UNAVAILABLE), request-hash + duration + summary traces; provenance on every evidence item; fabricated results impossible (model never calls tools). **Gap:** no transaction-velocity, amount-deviation, customer-profile, or sanctions/PEP tools; timeout path untested | `EvidenceTools`, `ToolRunner`, `DeterministicFixtures` |
| 4 | Spring AI implementation | 20 | **16** | Implemented (gaps) | `ChatClient` + system/user `PromptTemplate` params; `.entity()` typed output; `MessageChatMemoryAdvisor` + `CONVERSATION_ID=caseId`; JPA `ChatMemoryRepository`; `SimpleVectorStore` ingestion/search; provider config external; `AI_FALLBACK`→rules on failure. **Gaps:** `@Tool` annotations exist but are never registered via `.tools(...)` — tools are orchestrated app-side by design; no RAG `QuestionAnswerAdvisor` (manual retrieval instead) | `SpringAiFraudCaseAgent`, `SpringAiConfig`, `JpaChatMemoryRepository`, `CaseQaService` |
| 5 | LangChain4j implementation | 10 | **0** | Optional Not Implemented | Not present; points redistributed to metric 4 per framework scoring rule | — |
| 6 | RAG, embeddings, retrieval, citations | 15 | **12** | Implemented (gaps) | Front-matter parse + section chunking; rich metadata (docId/title/version/effectiveDate/owner/status/section/chunkId); withdrawn `POL-ESC-021` excluded at ingestion; configurable top-K=4, threshold=0.10; abstention logged; citations rendered with exact metadata; tests cover relevant/unrelated/blank/withdrawn/duplicate-query. **Gaps:** corpus is only 3 active docs — no AML, sanctions or customer-contact policies; hashing embedder is a toy by design; paraphrase/conflicting-policy cases lightly covered | `policies/*.md`, `PolicyIngestionService`, `PolicyRetriever`, `PolicyRetrievalTest` |
| 7 | Structured output, memory, human review | 8 | **8** | Implemented | Typed DTO + enums; validator rejects unknown enums, bad confidence, `humanReviewRequired=false`, malformed citations, uncited procedural claims — before persistence; memory window 12 msgs keyed by caseId; 5 review actions; rationale mandatory server-side (400 without it); no autonomous actions | `RecommendationValidator`, `FraudCaseRecommendation`, `recordReview`, `FraudCaseFlowTests` |
| 8 | Security, privacy, responsible AI, auditability | 8 | **7** | Implemented (minor gaps) | Zero committed secrets (scan-verified); env-only config; `MaskingRules` rejects 12+ digit runs + markup; system prompt declares statement untrusted data; append-only audit with SHA-256 payload hashes; least-privilege read-only tools; RBAC on every endpoint. **Gaps:** committed demo-password *defaults* (`agent123`…, env-overridable); no request-level correlation-ID filter; model name/version not stamped into audit metadata | `SecurityConfig`, `MaskingRules`, `AuditWriter`, `.gitignore`, `fraud-system.txt` |
| 9 | Testing, reliability, observability | 8 | **6** | Implemented (gaps) | 42 tests: tool determinism/contract, validator, masking, RAG + abstention, memory isolation, full MockMvc flow with role boundaries + 409-on-reinvestigate, Testcontainers MySQL. Actuator health/info/metrics; per-tool latency recorded. **Gaps:** no JaCoCo; no intake idempotency key (double POST creates second case); no request correlation-ID; timeout path has no test | `src/test/` (10 classes), `management.endpoints` |
| 10 | UI, docs, developer experience | 4 | **4** | Implemented | All 6 screens + case list; success/error flash alerts; labels/`aria-describedby`; README (setup, API table, sample curl, demo refs, limitations); AGENTS.md; `.env.example`, `application-example.yml`, Dockerfile, compose; no loading-spinner states (minor) | `templates/`, `README.md`, root files |
|  | **Total** | **100** | **86** | | | |

## 5. Framework Evaluation

### 5.1 Spring AI
- `ChatClient` built in `SpringAiConfig`; Anthropic autoconfigured (model/temperature/max-tokens via env).
- Prompts externalized to `prompts/fraud-system.txt` + `fraud-user.txt`; variables bound through `PromptTemplate`-style `.params(context.promptVariables())`.
- Typed structured output: `.call().entity(FraudCaseRecommendation.class)` then `validator.validate()` in both adapter and orchestrator — double enforcement.
- Memory: `MessageWindowChatMemory` (max 12) over `JpaChatMemoryRepository`; `CONVERSATION_ID = caseId` — per-case isolation proven by `MemoryIsolationTests`.
- Failure handling: orchestrator catches any adapter exception → `AI_FALLBACK` audit event + rules engine; `CaseQaService` degrades to deterministic grounded answer.
- **Not implemented:** model-side tool calling (`.tools(...)`) and `QuestionAnswerAdvisor`; tools/retrieval are deliberately app-side so the model interprets but never executes — the `@Tool`/`@ToolParam` annotations remain available for a future direct-callback variant.

### 5.2 LangChain4j
Optional Not Implemented — no LangChain4j dependency or code exists. Per rubric, metric 4 was scored out of 20.

### 5.3 Framework Parity
Not applicable — single framework.

## 6. Acceptance Scenario Results

| Scenario | Mode | Expected | Actual | Result | Evidence |
|---|---|---|---|---|---|
| New device + location anomaly + unrecognised CNP merchant | rules | Suspicious/unauthorised w/ cited evidence | `UNAUTHORISED_TRANSACTION`, HIGH, `FRAUD_INVESTIGATION`, conf 0.95, 7 evidence items | **PASS** | Live :8090 run + `fullFlowIntakeToReview` test |
| Recognised merchant / fulfilment dispute | rules | Merchant-dispute route | `MERCHANT_DISPUTE` → `DISPUTE_OPERATIONS` | **PASS** | `RulesFraudCaseAgentTest.t02` |
| Duplicate fingerprint posting | rules | Payment-ops route | `DUPLICATE_PROCESSING` → `PAYMENT_OPERATIONS` | **PASS** | `RulesFraudCaseAgentTest.t03` |
| Transaction record unavailable | rules | `INSUFFICIENT_EVIDENCE`, no fabrication | `INSUFFICIENT_EVIDENCE` → `MANUAL_REVIEW`, status `INCOMPLETE`, `missingInformation` populated | **PASS** | `t08MissingTransactionYieldsIncomplete`, `t04` rules test |
| No policy passage meets threshold | rules | Abstain, `MANUAL_REVIEW`, policy noted missing | Route downgraded, `missingInformation` flagged, zero citations | **PASS** | `RulesFraudCaseAgentTest.t05`, `PolicyRetrievalTest` |
| Retired/withdrawn policy matches text | — | Excluded | `POL-ESC-021` (WITHDRAWN) skipped at ingestion; can never be cited | **PASS** | `PolicyIngestionService` + `PolicyRetrievalTest` |
| Policy question with active source | rules | Grounded answer + docId/version/section | Deterministic answer + citation list | **PASS** | `followUpMessageReturnsAnswerAndCitations` |
| Question absent from corpus | rules | Explicit no-info | "No policy passage met the retrieval threshold" | **PASS** | `CaseQaService.deterministicAnswer`, abstention test |
| Review without rationale | — | Rejected | HTTP 400; UI requires field | **PASS** | `fullFlowIntakeToReview` (T12), `review.html` |
| Re-investigate routed case | — | Blocked | HTTP 409 `InvalidCaseStateException` | **PASS** | `fullFlowIntakeToReview` tail |
| Duplicate intake POST | — | Idempotent / deduped | Second case created (new `caseId`) — no dedup key | **PARTIAL** | `createCase` has no idempotency key |
| Malformed model output | both | Validate / safe fail | Validator throws `InvalidRecommendationException`; orchestrator logs `AI_FALLBACK` + uses rules | **PASS** | `RecommendationValidatorTest` (8 tests), `analyze()` |
| Two case IDs memory isolation | — | No cross-case recall | `ZEBRA-9` fact invisible to case B | **PASS** | `MemoryIsolationTests` |
| Prompt injection in statement | — | Treated as data | Markup rejected at intake; system prompt instructs untrusted-data handling; model never executes actions anyway | **PARTIAL** | `t04RejectsMarkupInStatement`, `fraud-system.txt` L12 — no dedicated injection test |
| Five+ transactions in 10 min (velocity) | — | Velocity indicator | **Not testable** — no velocity tool | **GAP** | `EvidenceTools` lacks velocity tool |
| Sanctions/PEP screening match | — | Mandatory escalation | **Not testable** — no screening tool | **GAP** | `EvidenceTools` lacks screening tool |
| `spring-ai` mode end-to-end | spring-ai | Same flow via Claude | Not executed live (no key in CI); adapter compiles + is conditionally wired; contract identical via shared validator | **PARTIAL** | `SpringAiFraudCaseAgent`, `analyze()` |

## 7. Strengths
1. **Hard safety boundary enforced in code, not prompts** — `humanReviewRequired` is un-waivable in `RecommendationValidator`; tools are read-only; state transitions only via `FraudCaseService`; every path (including AI-failure fallback) can only ever end in a safe `INCOMPLETE`/`MANUAL_REVIEW` state.
2. **Deterministic, honest evidence pipeline** — seeded hash fixtures give identical input→output; `ToolRunner` bounds every call and emits redacted traces; unavailable data is reported as `UNAVAILABLE`/`missingInformation`, never invented.
3. **Strong test pyramid for a capstone** — 42 tests across unit, contract, RAG/abstention, memory isolation, MockMvc role-boundary flow, and a real Testcontainers MySQL+Flyway run; verified green.

## 8. Gaps and Risks

| Priority | Gap or Risk | Impact | Evidence | Recommended Fix |
|---|---|---|---|---|
| High | Model-side tool calling not wired (`@Tool` methods never registered via `.tools(...)`) | Rubric expects tool callbacks; adapter can't demonstrate model-initiated tool use | `SpringAiFraudCaseAgent.analyze` | Add `.tools(evidenceTools)` on the prompt or keep app-side design and document it as an intentional least-privilege choice in README |
| High | No velocity, amount-deviation, customer-profile, or sanctions/PEP tools | Two acceptance scenarios untestable; thinner evidence breadth | `EvidenceTools` (8 tools) | Add `getTransactionVelocity` + `screenSanctionsPep` fixtures (read-only, deterministic) |
| Medium | No intake idempotency key — duplicate POST creates a second case | Duplicate-submission scenario only partially met | `createCase` | Unique constraint on `(maskedCustomerRef, transactionRef)` or client-supplied idempotency key → 409 |
| Medium | Corpus has only 3 active policies; no AML/sanctions/customer-contact docs | RAG breadth; conflicting-source behavior untested | `policies/` | Add 1 AML-escalation + 1 customer-contact doc; add conflicting-policy retrieval test |
| Medium | No coverage tool, no request correlation-ID, model name/version absent from audit | Observability scoring; audit completeness | `pom.xml`, `AuditWriter` | JaCoCo plugin; `OncePerRequestFilter` for `X-Correlation-ID`; stamp `spring.ai.anthropic.chat.options.model` into `INVESTIGATION_COMPLETED` events |
| Low | Demo-password defaults committed (`agent123`…) | Low — synthetic env, env-overridable — but scanners flag it | `application.yml` 43–46 | Remove defaults; require env vars, or document loudly |
| Low | Timeout path in `ToolRunner` untested; hashing embedder is intentionally naive | Reliability evidence; retrieval quality | `ToolRunner`, `HashingEmbeddingModel` | Unit-test a sleeping supplier → TIMEOUT status; swap embedder when real provider available |

## 9. Security and Responsible AI Findings

| Severity | Finding | File or Component | Remediation |
|---|---|---|---|
| Low | Demo credentials shipped as plaintext defaults (env-overridable, synthetic) | `application.yml` 43–46 | Require `*_PASSWORD` env vars; remove defaults |
| Low | No request correlation-ID propagated between UI/API and audit trail | `SecurityConfig` / filters | Add correlation-ID filter; include in audit summaries |
| Low | Model identifier not recorded in audit metadata (mode is: `RULES`/`SPRING_AI`) | `InvestigationOrchestrator` | Include configured model name in `INVESTIGATION_COMPLETED` |
| Info | Prompt-injection mitigations are defense-in-depth but untested end-to-end | `fraud-system.txt`, `MaskingRules` | Add an injection-string intake/follow-up test asserting statement is treated as data |
| Info | Secret-pattern review: **no live credentials found** — only env placeholders (`${ANTHROPIC_API_KEY}`, `sk-ant-...` template, `test-placeholder-key`) | `.env.example`, `application*.yml` | None — verified via `git ls-files` scan |

## 10. Recommended Improvements

### Critical
- None — the safety invariants hold end-to-end.

### High
1. Wire `.tools(evidenceTools)` in `SpringAiFraudCaseAgent` (or document the intentional app-side design) — closes the rubric's tool-callback gap.
2. Add `getTransactionVelocity` and `screenSanctionsPep` deterministic tools to cover the two untestable acceptance scenarios.

### Medium
3. Intake idempotency: unique key on `(maskedCustomerRef, transactionRef)` → 409 on duplicate.
4. Expand policy corpus (AML escalation, customer-contact) + paraphrase/conflicting-source retrieval tests.
5. JaCoCo + `X-Correlation-ID` filter + model name in audit metadata.

### Low
6. Remove committed demo-password defaults; add `ToolRunner` timeout unit test; add a UI loading state on "Start investigation".

## 11. Definition-of-Done Checklist
- [x] Fraud case intake works with synthetic data
- [x] Deterministic tools provide traceable evidence
- [x] Structured recommendations are validated
- [x] Policy answers are grounded and cited
- [x] Human review is mandatory
- [x] Conversation/case memory is isolated
- [x] Secrets are not stored in the repository
- [x] Error and no-information states are handled safely
- [x] Automated tests pass (42/42)
- [x] README enables another participant to run the project

## 12. Final Verdict

**Demo Ready** (86/100).

The capstone demonstrates the required architecture end-to-end and, more importantly, gets the *safety-critical* parts right by construction: the model can only interpret validated evidence, every recommendation is schema-checked and advisory-only, and final disposition is structurally impossible without a human reviewer — verified live against real MySQL (intake → 7 traced tool calls → cited recommendation → audited disposition → `ROUTED`).

The score is held below ~90 by four concrete, fixable items rather than design flaws: model-side tool callbacks aren't exercised (tools run app-side by design, but the rubric expects the callback path), two evidence domains (velocity, sanctions/PEP) are missing, intake isn't idempotent, and observability lacks a coverage report and correlation IDs. None of these compromise the demo; all have low-risk remediation paths listed in §8/§10.
