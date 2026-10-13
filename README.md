# Agent-RAG Enterprise Knowledge QA

Agent-RAG is a Spring Boot based enterprise knowledge-base QA backend. It keeps the core RAG pipeline self-built and observable: document parsing, Markdown-aware chunking, hybrid retrieval, graph relation recall, scoring, rerank, source citation, execution trace, trace-derived memory, and fixed eval regression.

The project is designed for interview/demo scenarios where the focus is not only "can answer", but also "why this answer was retrieved, which source supports it, and whether the graph-enhanced pipeline improves over baseline RAG".

## Highlights

- Multi-format document ingestion for TXT, Markdown, PDF, Word, and PPT.
- Markdown-aware chunking with source and section metadata.
- Baseline RAG and graph-enhanced RAG comparison.
- Rule-based Query Rewrite with multi-query recall fallback.
- Candidate scoring and rerank diagnostics.
- Structured source citation owned by the backend, not generated freely by the model.
- Fixed eval set under `docs/eval` with latest metrics and markdown reports.
- Trace-derived memory for query history, entity focus, failure cases, success cases, and procedural rules.
- Minimal LangChain4j integration at the answer generation layer only.

## Checked-in Eval Snapshot

The checked-in rule-mode report predates the evidence and quality-metric milestones below. These numbers are historical, not a rerun of the current branch:

| metric | value |
| --- | ---: |
| total | 15 |
| baselineHitRate | 0.60 |
| graphHitRate | 0.9333 |
| sourceHitRate | 0.80 |
| NO_CANDIDATES_RECALLED | 0 |

The tuning path is documented in `docs/eval/rag_tuning_summary.md`. To produce current numbers, prepare the database and run `POST /eval/run` as described below.

## Architecture

```text
/ask
-> AskController
-> AskService
-> AgentRouterService
-> AgentExecutor
-> QueryUnderstanding / Memory lookup
-> ChunkSearchTool / GraphSearchTool / SqlSearchTool
-> QueryRewriteService
-> SearchService / RelationSearchService
-> RetrievalScoreService
-> RerankService
-> AnswerGenerateTool
-> RuleBasedAnswerClient or LangChain4jAnswerClient
-> AskResponse + sources + debug trace
```

## Main APIs

| API | Purpose |
| --- | --- |
| `POST /ask` | Run baseline or graph-enhanced QA. |
| `GET /search` | Keyword search over document chunks. |
| `POST /upload` | Upload and parse documents. |
| `GET /documents/{documentId}/chunks` | Inspect parsed chunks. |
| `GET /entities/recent` | Inspect extracted entities. |
| `GET /relations/by-entity` | Inspect relation evidence. |
| `POST /eval/run` | Run fixed RAG eval and regenerate latest reports. |
| `GET /eval/runs?limit=20&before=ID` | Browse stored runs, newest first. |
| `GET /eval/runs/{id}` | Inspect a run's status, dataset digest, and counters. |
| `GET /eval/runs/{id}/cases?limit=20&after=POSITION` | Page through immutable case snapshots. |
| `GET /eval/compare?base=ID&candidate=ID` | Compare two completed runs of the same question set. |
| `GET /eval/gate?base=ID&candidate=ID` | Decide whether a candidate exceeds a regression budget. |

## LangChain4j Integration

LangChain4j is intentionally connected only to answer generation:

- default mode: `answer.mode=rule`
- optional mode: `answer.mode=langchain4j`
- fallback: failed model calls return the rule-based answer
- sources remain backend-structured and auditable

Detailed notes and real-model validation steps are in `docs/langchain4j_integration.md`.

## Install and Run Locally

Prerequisites:

- JDK 17
- Maven (3.9.6 was used for the documented local verification)
- A reachable Oracle database (tested with Oracle Free 26ai), SQL*Plus, and an empty application schema for a fresh setup

Run the following commands from the repository root. On a machine with network access, populate Maven's dependency cache and build the JAR:

```powershell
mvn -B clean verify
```

With dependencies already cached, the same build can be checked offline:

```powershell
mvn -o -B clean verify
```

A fresh machine without a populated Maven cache cannot use the offline command immediately. The build writes `target/rag_api-1.0-SNAPSHOT.jar` and test reports under `target/surefire-reports/`.

Ask a database administrator to create a dedicated empty user in the target PDB with `CREATE SESSION`, `CREATE TABLE`, `CREATE SEQUENCE`, and a tablespace quota. From the repository's `sql/` directory, run the ordered bootstrap as that user; SQL*Plus prompts for the password:

```powershell
cd sql
sqlplus -L RAG_APP@//localhost:1521/FREEPDB1 @bootstrap_oracle.sql
cd ..
```

`bootstrap_oracle.sql` creates nine tables, their sequences and indexes, and one `KNOWLEDGE` document with one chunk for fixed eval. The last two tables retain fixed-eval runs and ordered case snapshots. It is for an **empty schema** and deliberately fails on existing objects. It never drops tables. To upgrade a schema that already has the original seven tables, apply the additive `sql/create_eval_history_tables.sql` once as the application user. In particular, `RAG_ENTITY_RELATIONS.CONFIDENCE` is present and both confidence columns use Oracle `NUMBER(10,4)`.

Set the database connection for a local run:

```powershell
$env:RAG_DB_URL = 'jdbc:oracle:thin:@//localhost:1521/FREEPDB1'
$env:RAG_DB_USER = 'RAG_APP'
$env:RAG_DB_PASSWORD = '<your-db-password>'
```

`application.yml` reads these variables. URL and username have local defaults; the password has no default. Hibernate defaults to `validate`, so startup checks the SQL schema instead of creating or changing it. If an existing deployment needs the previous behavior temporarily, set `RAG_DDL_AUTO=none` after reviewing schema compatibility.

Start the backend:

```powershell
mvn spring-boot:run
```

Run eval in another terminal from the repository root:

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/eval/run
```

The endpoint reads `docs/eval/questions.json` and writes `latest_eval_results.json` plus `latest_eval_report.md` to `docs/eval/` by default. Set `rag.eval.output-dir` to use another directory, as the integration test does to protect checked-in historical reports. Its new per-question fields are `wrongAnswer`, `citationMatched`, `citationMissing`, and `abstained`; the summary/report include their counts. The wrong-answer flag checks expected keywords in the answer itself. The citation flag requires an evidence sentence with an expected answer keyword and an expected source token in either the source name or evidence. These are deterministic review signals, not semantic proof.

Each successful response also contains `runId`. The run and every case are stored in Oracle; later evals cannot replace them. For example, `GET /eval/runs?limit=10` returns recent run IDs, `GET /eval/runs/42/cases?limit=20` returns ordered case snapshots, and `GET /eval/compare?base=41&candidate=42` reports per-case hit, wrong-answer, citation, and abstention changes. Follow `nextBefore` or `nextAfter` for another page. The comparison requires both runs to be complete and to have the same SHA-256 digest of `docs/eval/questions.json`; a mismatch returns HTTP 409. The latest files remain compatibility outputs; the database records are the historical source of truth.

For a deterministic CI decision, call `GET /eval/gate?base=41&candidate=42`. Its default budgets allow zero graph regressions, newly wrong answers, lost citations, and new abstentions. Add nonnegative `maxGraphRegressions`, `maxNewWrongAnswers`, `maxCitationsLost`, or `maxNewAbstentions` query parameters to tolerate known changes. The response returns `passed`, the chosen policy, observed comparison, and violations with case IDs. An incompatible or incomplete pair returns HTTP 409. CI should inspect `passed`; HTTP 200 means the comparison ran, not that the candidate passed.

To repeat the real Oracle-backed endpoint tests in a dedicated schema prepared by the bootstrap, set `RAG_E2E_ENABLED=true` along with the three `RAG_DB_*` variables, then run `mvn -B '-Dtest=LocalEvalEndToEndTest,EvalHistoryEndToEndTest' test`. Without the opt-in variable, normal builds skip these database tests. They call `POST /eval/run`, check seeded `EVAL-005`, verify persisted history and pagination, and reject invalid or incompatible comparisons. See `docs/design/evidence_eval_milestones.md` for the red/green record.

An optional model-backed answer mode is documented in `docs/langchain4j_integration.md`; rule mode needs no model API key.

Example request once the database is ready:

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/ask -ContentType 'application/json' -Body '{"question":"SearchService 的作用是什么？","mode":"graph"}'
```

## Repository Docs

- `docs/eval/questions.json`: fixed eval question set.
- `docs/eval/latest_eval_results.json`: historical per-question output, regenerated by `/eval/run`.
- `docs/eval/latest_eval_report.md`: latest baseline vs graph eval report.
- `docs/eval/rag_tuning_summary.md`: v0-v5 tuning summary.
- `docs/eval/langchain4j_eval_comparison.md`: rule vs LangChain4j comparison template.
- `docs/langchain4j_integration.md`: minimal LangChain4j integration notes.
- `docs/design/memory_design.md`: agent memory design notes.
- `docs/design/multi_agent_design.md`: multi-agent evolution design.
- `docs/design/evidence_eval_milestones.md`: five red/green development intervals, design choices, and an explicit platform milestone qualification audit.
- `docs/design/eval_history_rfc.md`: persistent fixed-eval history and regression comparison design.
- `docs/roadmap.md`: five concrete follow-up tasks with affected code areas.
