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

## Current Eval Result

Latest rule-mode eval summary:

| metric | value |
| --- | ---: |
| total | 15 |
| baselineHitRate | 0.60 |
| graphHitRate | 0.9333 |
| sourceHitRate | 0.80 |
| NO_CANDIDATES_RECALLED | 0 |

The tuning path is documented in `docs/eval/rag_tuning_summary.md`.

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

## Run Locally

Prerequisites:

- JDK 17
- Maven
- Oracle-compatible database configured in `application.yml`

Run tests:

```powershell
mvn test
```

Start the backend:

```powershell
mvn spring-boot:run
```

Run eval:

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/eval/run
```

## Repository Docs

- `docs/eval/questions.json`: fixed eval question set.
- `docs/eval/latest_eval_report.md`: latest baseline vs graph eval report.
- `docs/eval/rag_tuning_summary.md`: v0-v5 tuning summary.
- `docs/eval/langchain4j_eval_comparison.md`: rule vs LangChain4j comparison template.
- `docs/langchain4j_integration.md`: minimal LangChain4j integration notes.
- `docs/design/memory_design.md`: agent memory design notes.
- `docs/design/multi_agent_design.md`: multi-agent evolution design.
