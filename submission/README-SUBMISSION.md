# Capstone Submission — Amit Raj (amit.raj7@wipro.com)

**Project:** Intelligent Payment Fraud Investigation and Dispute Resolution Agent
**Repository:** https://github.com/amitkushwaha0104-bit/DevinTraining

## Contents

| Item | Description |
|---|---|
| `SELF_EVALUATION_REPORT.md` | Rubric-based self-evaluation — **86/100, Demo Ready** |
| `fraud-investigation-agent-source.zip` | Complete project source (unzip → `mvn verify` / `mvn spring-boot:run`) |
| `fraud-investigation-agent/` | Same source, unzipped for browsing |
| `demo-video.mp4` | 62-second narrated-slide demo of the full case lifecycle |
| `screenshots/` | 8 captured screens (login, cases, intake, workspace, evidence, citations, review, audit) |

## Quick start

```bash
unzip fraud-investigation-agent-source.zip -d fraud-app && cd fraud-app
mvn verify                # 42 tests; Testcontainers needs Docker
mvn spring-boot:run       # needs MySQL 8 (or docker compose up --build)
```

UI: http://localhost:8080 — users `agent/agent123`, `investigator/invest123`,
`reviewer/review123`, `auditor/audit123`, `lead/review123` (synthetic demo creds).

Default mode `rules` needs no API key. For Claude: `PROCESSOR_MODE=spring-ai` + `ANTHROPIC_API_KEY`.

All data is synthetic; the assistant only recommends — final disposition always belongs to a human reviewer.
