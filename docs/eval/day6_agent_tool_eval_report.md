# Day 6 Agent Tool Eval Report

| ID | Question Type | Question | RouterOnlyHit | ToolAgentHit | GraphHit | SqlSearchHit | ChunkSearchHit | TraceHit | SourceHit | EvidenceHit | ExpectedToolsHit | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| A6-001 | TABLE_FIELDS | RAG_DOCUMENT_CHUNKS 表有哪些字段 | Y | Y | Y | N | N | Y | Y | Y | Y | trace hit; graph table fields |
| A6-002 | SQL_METADATA_FALLBACK | RAG_ENTITY_RELATIONS 表有哪些字段 | N | Y | Y | Y | N | Y | Y | Y | Y | tool-agent improved hit; sql search hit; trace hit; sql metadata fallback |
| A6-003 | SQL_METADATA_FALLBACK | RAG_DOCUMENTS 表有哪些字段 | N | Y | Y | Y | N | Y | Y | Y | Y | tool-agent improved hit; sql search hit; trace hit; sql metadata fallback |
| A6-004 | NO_ANSWER | RAG_UNKNOWN_TABLE 表有哪些字段 | Y | Y | Y | Y | N | Y | Y | N | Y | sql search hit; trace hit; graph miss plus sql miss |
| A6-005 | FIELD_OWNERSHIP | EMBEDDING_STATUS 属于哪张表 | Y | Y | Y | N | N | Y | Y | Y | Y | trace hit; field ownership relation |
| A6-006 | GRAPH_RELATION | /ask 依赖哪些服务 | Y | Y | Y | N | N | Y | Y | Y | Y | trace hit; api to service relation |
| A6-007 | CHUNK_BASELINE | /search 的排序规则是什么 | Y | Y | N | N | Y | Y | Y | Y | Y | trace hit; chunk baseline |
| A6-008 | GRAPH_RELATION | PDF 导入依赖什么库 | Y | Y | Y | N | N | Y | Y | Y | Y | trace hit; library dependency |
| A6-009 | CHUNK_BASELINE | SearchService 的作用是什么 | Y | Y | N | N | Y | Y | Y | Y | Y | trace hit; definition chunk baseline |
| A6-010 | NO_ANSWER | 这个系统怎么做 Kubernetes 调度 | Y | Y | N | N | Y | Y | Y | N | Y | trace hit; no answer baseline |

## Summary

- totalQuestions: 10
- routerOnlyHitCount: 8
- toolAgentHitCount: 10
- graphHitCount: 7
- sqlSearchHitCount: 3
- chunkSearchHitCount: 3
- traceHitCount: 10
- sourceHitCount: 10
- evidenceHitCount: 8
- expectedToolsHitCount: 10
