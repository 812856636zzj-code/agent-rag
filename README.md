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

`bootstrap_oracle.sql` creates seven tables, their sequences and indexes, and one `KNOWLEDGE` document with one chunk for fixed eval. It is for an **empty schema** and deliberately fails on existing objects. It never drops tables. To upgrade an existing schema, review and apply targeted migrations instead. In particular, `RAG_ENTITY_RELATIONS.CONFIDENCE` is now present and both confidence columns use Oracle `NUMBER(10,4)`.

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

To repeat the real Oracle-backed endpoint test in a dedicated schema prepared by the bootstrap, set `RAG_E2E_ENABLED=true` along with the three `RAG_DB_*` variables, then run `mvn -B -Dtest=LocalEvalEndToEndTest test`. Without the opt-in variable, normal builds skip this database test. The test calls `POST /eval/run`, checks seeded `EVAL-005`, and compares response counts with its isolated JSON report. See `docs/design/evidence_eval_milestones.md` for the red/green record.

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
- `docs/design/evidence_eval_milestones.md`: four red/green development intervals, design choices, and an explicit platform milestone qualification audit.
- `docs/roadmap.md`: five concrete follow-up tasks with affected code areas.
