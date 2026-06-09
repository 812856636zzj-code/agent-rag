# Day 6 Graph Eval Report

| ID | 问题类型 | 问题 | 期望模式 | Baseline命中 | Graph命中 | Relation命中 | Source命中 | Evidence命中 | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| D6-001 | TABLE_FIELDS | RAG_DOCUMENT_CHUNKS 表有哪些字段 | GRAPH | 否 | 是 | 是 | 是 | 是 | graph improved hit; relation hit; source hit; evidence hit; baselineKeywordHitCount=1; graphKeywordHitCount=6; expectedKeywordCount=6; baselineKeywordHitRatio=0.17; graphKeywordHitRatio=1.00 |
| D6-002 | TABLE_RELATION | EMBEDDING_STATUS 属于哪张表 | GRAPH | 否 | 是 | 否 | 是 | 是 | graph improved hit; source hit; evidence hit; baselineKeywordHitCount=0; graphKeywordHitCount=2; expectedKeywordCount=2; baselineKeywordHitRatio=0.00; graphKeywordHitRatio=1.00 |
| D6-003 | SERVICE_RELATION | /ask 依赖哪些服务 | GRAPH | 是 | 是 | 是 | 是 | 是 | relation hit; source hit; evidence hit; baselineKeywordHitCount=3; graphKeywordHitCount=3; expectedKeywordCount=3; baselineKeywordHitRatio=1.00; graphKeywordHitRatio=1.00 |
| D6-004 | SORT_RULE | /search 的排序规则是什么 | BASELINE | 是 | 是 | 否 | 是 | 是 | source hit; evidence hit; baselineKeywordHitCount=2; graphKeywordHitCount=2; expectedKeywordCount=3; baselineKeywordHitRatio=0.67; graphKeywordHitRatio=0.67 |
| D6-005 | LIBRARY_RELATION | PDF 导入依赖什么库 | GRAPH | 是 | 是 | 是 | 是 | 是 | relation hit; source hit; evidence hit; baselineKeywordHitCount=1; graphKeywordHitCount=1; expectedKeywordCount=1; baselineKeywordHitRatio=1.00; graphKeywordHitRatio=1.00 |
| D6-006 | SERVICE_RELATION | PDFBox 和哪些服务有关 | GRAPH | 是 | 是 | 否 | 是 | 是 | source hit; evidence hit; baselineKeywordHitCount=3; graphKeywordHitCount=1; expectedKeywordCount=3; baselineKeywordHitRatio=1.00; graphKeywordHitRatio=0.33 |
| D6-007 | DEFINITION | chunk 大小在哪里定义 | BASELINE | 是 | 是 | 否 | 是 | 是 | source hit; evidence hit; baselineKeywordHitCount=1; graphKeywordHitCount=1; expectedKeywordCount=3; baselineKeywordHitRatio=0.33; graphKeywordHitRatio=0.33 |
| D6-008 | GENERAL_DEFINITION | SearchService 的作用是什么 | BASELINE | 是 | 是 | 否 | 是 | 是 | source hit; evidence hit; baselineKeywordHitCount=2; graphKeywordHitCount=2; expectedKeywordCount=3; baselineKeywordHitRatio=0.67; graphKeywordHitRatio=0.67 |
| D6-009 | SERVICE_RELATION | /search 依赖哪些服务 | GRAPH | 是 | 是 | 是 | 否 | 否 | relation hit; baselineKeywordHitCount=1; graphKeywordHitCount=1; expectedKeywordCount=2; baselineKeywordHitRatio=0.50; graphKeywordHitRatio=0.50 |
| D6-010 | NO_ANSWER | 这个系统怎么做 Kubernetes 调度 | BASELINE | 是 | 是 | 否 | 否 | 否 | baselineKeywordHitCount=1; graphKeywordHitCount=1; expectedKeywordCount=3; baselineKeywordHitRatio=0.33; graphKeywordHitRatio=0.33 |

## Summary

- totalQuestions: 10
- baselineHitCount: 8
- graphHitCount: 10
- relationHitCount: 4
- sourceHitCount: 8
- evidenceHitCount: 8
