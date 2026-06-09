# Latest RAG Eval Report

## Summary

- total: 15
- baselineHitRate: 0.60 (9/15)
- graphHitRate: 0.93 (14/15)
- sourceHitRate: 0.80 (12/15)

## Baseline Vs Graph

| ID | Type | Question | BaselineHit | GraphHit | SourceHit | BaselineReadability | GraphReadability | Noise | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| EVAL-001 | RAG | /ask 依赖哪些核心服务？ | Y | Y | Y | 5 | 5 |  |  |
| EVAL-002 | RAG | SearchService 的作用是什么？ | Y | Y | Y | 5 | 5 |  |  |
| EVAL-003 | RAG | ContextBuilderService 会组装哪些上下文信息？ | Y | Y | Y | 5 | 5 |  |  |
| EVAL-004 | RAG | AnswerBuilderService 在 /ask 流程中负责什么？ | Y | Y | Y | 5 | 5 |  |  |
| EVAL-005 | entity | RAG_DOCUMENT_CHUNKS 表有哪些关键字段？ | Y | Y | Y | 5 | 5 |  |  |
| EVAL-006 | entity | embeddingStatus 属于哪张表？ | Y | Y | N | 5 | 5 |  | expected source not found in graph sources |
| EVAL-007 | entity | RAG_ENTITY_RELATIONS 表有哪些字段？ | N | Y | Y | 5 | 3 |  | graph improved keyword hit |
| EVAL-008 | relation | /search 的排序规则是什么？ | Y | Y | Y | 5 | 5 |  |  |
| EVAL-009 | relation | /ask 和 SearchService 是什么关系？ | Y | Y | Y | 5 | 5 |  |  |
| EVAL-010 | relation | /ask 和 ContextBuilderService 是什么关系？ | Y | Y | Y | 5 | 5 |  |  |
| EVAL-011 | graph | 图增强问答相比 baseline 会多利用什么信息？ | N | Y | Y | 3 | 5 |  | graph improved keyword hit; graph readability higher |
| EVAL-012 | graph | RelationQueryService 的作用是什么？ | N | Y | Y | 3 | 5 |  | graph improved keyword hit; graph readability higher |
| EVAL-013 | RAG | QueryUnderstandingService 会识别哪些查询信息？ | N | Y | Y | 3 | 5 |  | graph improved keyword hit; graph readability higher |
| EVAL-014 | sources | 回答里应该如何呈现来源证据？ | N | N | N | 3 | 5 |  | graph readability higher; expected source not found in graph sources |
| EVAL-015 | noise | 评测回答中是否应该出现 TEST_SPEC 或 oracle.sql.CLOB 这类噪声？ | N | Y | N | 3 | 5 | graph | graph improved keyword hit; graph readability higher; graph noise detected; expected source not found in graph sources |

## Type Hit Rates

| Type | Total | BaselineHitRate | GraphHitRate | SourceHitRate |
| --- | --- | --- | --- | --- |
| RAG | 5 | 0.80 | 1.00 | 1.00 |
| entity | 3 | 0.67 | 1.00 | 0.67 |
| relation | 3 | 1.00 | 1.00 | 1.00 |
| graph | 2 | 0.00 | 1.00 | 1.00 |
| sources | 1 | 0.00 | 0.00 | 0.00 |
| noise | 1 | 0.00 | 1.00 | 0.00 |

## Diagnosis Reason Summary

### RERANK_NO_EFFECT (14)

- question: /ask 依赖哪些核心服务？
  expected: SearchService ContextBuilderService AnswerBuilderService
  missing: 
  rewrite: /ask 依赖哪些核心服务？ RELATION 关系 依赖 DEPENDS_ON API_BELONGS_TO_SERVICE BELONGS_TO /ask
  rerank: before=[321] after=[321]
  reasons: RERANK_NO_EFFECT RERANK_SINGLE_CANDIDATE
- question: SearchService 的作用是什么？
  expected: SearchService keyword entity hybrid
  missing: hybrid
  rewrite: SearchService 的作用是什么？ ENTITY 作用 职责 功能 负责 调用链 SearchService
  rerank: before=[321, 161, 122, 123, 5] after=[321, 161, 122, 123, 5]
  reasons: RERANK_NO_EFFECT RERANK_CONFIDENT_STABLE ANSWER_MISSING_EXPECTED_KEYWORDS
- question: ContextBuilderService 会组装哪些上下文信息？
  expected: matchedEntities matchedRelations retrievedChunks evidenceTexts
  missing: matchedEntities matchedRelations retrievedChunks evidenceTexts
  rewrite: ContextBuilderService 会组装哪些上下文信息？ ENTITY matchedEntities matchedRelations retrievedChunks evidenceTexts StructuredContext ContextBuilderService
  rerank: before=[321, 161] after=[321, 161]
  reasons: RERANK_NO_EFFECT RERANK_CONFIDENT_STABLE ANSWER_MISSING_EXPECTED_KEYWORDS

### RERANK_SINGLE_CANDIDATE (5)

- question: /ask 依赖哪些核心服务？
  expected: SearchService ContextBuilderService AnswerBuilderService
  missing: 
  rewrite: /ask 依赖哪些核心服务？ RELATION 关系 依赖 DEPENDS_ON API_BELONGS_TO_SERVICE BELONGS_TO /ask
  rerank: before=[321] after=[321]
  reasons: RERANK_NO_EFFECT RERANK_SINGLE_CANDIDATE
- question: embeddingStatus 属于哪张表？
  expected: embeddingStatus RAG_DOCUMENT_CHUNKS EMBEDDING_STATUS
  missing: EMBEDDING_STATUS
  rewrite: embeddingStatus 属于哪张表？ TABLE_OR_FIELD 字段 列 COLUMN TABLE USER_TAB_COLUMNS embeddingStatus
  rerank: before=[322] after=[322]
  reasons: RERANK_NO_EFFECT RERANK_SINGLE_CANDIDATE SOURCE_NOT_MATCHED ANSWER_MISSING_EXPECTED_KEYWORDS ANSWER_RIGHT_SOURCE_WRONG
- question: RAG_ENTITY_RELATIONS 表有哪些字段？
  expected: SOURCE_ENTITY_ID TARGET_ENTITY_ID RELATION_TYPE DOCUMENT_ID CHUNK_ID EVIDENCE_TEXT CREATED_AT CONFIDENCE
  missing: 
  rewrite: RAG_ENTITY_RELATIONS 表有哪些字段？ TABLE_OR_FIELD 字段 列 COLUMN TABLE USER_TAB_COLUMNS RAG_ENTITY_RELATIONS
  rerank: before=[321] after=[321]
  reasons: RERANK_NO_EFFECT RERANK_SINGLE_CANDIDATE GRAPH_BETTER_THAN_BASELINE

### RERANK_CONFIDENT_STABLE (8)

- question: SearchService 的作用是什么？
  expected: SearchService keyword entity hybrid
  missing: hybrid
  rewrite: SearchService 的作用是什么？ ENTITY 作用 职责 功能 负责 调用链 SearchService
  rerank: before=[321, 161, 122, 123, 5] after=[321, 161, 122, 123, 5]
  reasons: RERANK_NO_EFFECT RERANK_CONFIDENT_STABLE ANSWER_MISSING_EXPECTED_KEYWORDS
- question: ContextBuilderService 会组装哪些上下文信息？
  expected: matchedEntities matchedRelations retrievedChunks evidenceTexts
  missing: matchedEntities matchedRelations retrievedChunks evidenceTexts
  rewrite: ContextBuilderService 会组装哪些上下文信息？ ENTITY matchedEntities matchedRelations retrievedChunks evidenceTexts StructuredContext ContextBuilderService
  rerank: before=[321, 161] after=[321, 161]
  reasons: RERANK_NO_EFFECT RERANK_CONFIDENT_STABLE ANSWER_MISSING_EXPECTED_KEYWORDS
- question: AnswerBuilderService 在 /ask 流程中负责什么？
  expected: AnswerBuilderService StructuredContext 回答
  missing: StructuredContext 回答
  rewrite: AnswerBuilderService 在 /ask 流程中负责什么？ ENTITY 作用 职责 功能 负责 调用链 AnswerBuilderService /ask
  rerank: before=[321, 161, 122, 123, 5] after=[321, 161, 122, 123, 5]
  reasons: RERANK_NO_EFFECT RERANK_CONFIDENT_STABLE ANSWER_MISSING_EXPECTED_KEYWORDS

### ANSWER_MISSING_EXPECTED_KEYWORDS (10)

- question: SearchService 的作用是什么？
  expected: SearchService keyword entity hybrid
  missing: hybrid
  rewrite: SearchService 的作用是什么？ ENTITY 作用 职责 功能 负责 调用链 SearchService
  rerank: before=[321, 161, 122, 123, 5] after=[321, 161, 122, 123, 5]
  reasons: RERANK_NO_EFFECT RERANK_CONFIDENT_STABLE ANSWER_MISSING_EXPECTED_KEYWORDS
- question: ContextBuilderService 会组装哪些上下文信息？
  expected: matchedEntities matchedRelations retrievedChunks evidenceTexts
  missing: matchedEntities matchedRelations retrievedChunks evidenceTexts
  rewrite: ContextBuilderService 会组装哪些上下文信息？ ENTITY matchedEntities matchedRelations retrievedChunks evidenceTexts StructuredContext ContextBuilderService
  rerank: before=[321, 161] after=[321, 161]
  reasons: RERANK_NO_EFFECT RERANK_CONFIDENT_STABLE ANSWER_MISSING_EXPECTED_KEYWORDS
- question: AnswerBuilderService 在 /ask 流程中负责什么？
  expected: AnswerBuilderService StructuredContext 回答
  missing: StructuredContext 回答
  rewrite: AnswerBuilderService 在 /ask 流程中负责什么？ ENTITY 作用 职责 功能 负责 调用链 AnswerBuilderService /ask
  rerank: before=[321, 161, 122, 123, 5] after=[321, 161, 122, 123, 5]
  reasons: RERANK_NO_EFFECT RERANK_CONFIDENT_STABLE ANSWER_MISSING_EXPECTED_KEYWORDS

### SOURCE_NOT_MATCHED (3)

- question: embeddingStatus 属于哪张表？
  expected: embeddingStatus RAG_DOCUMENT_CHUNKS EMBEDDING_STATUS
  missing: EMBEDDING_STATUS
  rewrite: embeddingStatus 属于哪张表？ TABLE_OR_FIELD 字段 列 COLUMN TABLE USER_TAB_COLUMNS embeddingStatus
  rerank: before=[322] after=[322]
  reasons: RERANK_NO_EFFECT RERANK_SINGLE_CANDIDATE SOURCE_NOT_MATCHED ANSWER_MISSING_EXPECTED_KEYWORDS ANSWER_RIGHT_SOURCE_WRONG
- question: 回答里应该如何呈现来源证据？
  expected: 证据 sources evidence
  missing: sources evidence
  rewrite: 回答里应该如何呈现来源证据？ SOURCE source 依据 来源 证据 文档 sources evidence
  rerank: before=[5, 161, 322] after=[5, 161, 322]
  reasons: RERANK_NO_EFFECT RERANK_CONFIDENT_STABLE SOURCE_NOT_MATCHED ANSWER_MISSING_EXPECTED_KEYWORDS
- question: 评测回答中是否应该出现 TEST_SPEC 或 oracle.sql.CLOB 这类噪声？
  expected: 不应该 噪声 TEST_SPEC oracle.sql.CLOB
  missing: 不应该 噪声 TEST_SPEC oracle.sql.CLOB
  rewrite: 评测回答中是否应该出现 TEST_SPEC 或 oracle.sql.CLOB 这类噪声？ QUALITY_OR_NOISE 噪声 异常 问题 失败 误差 命中率低 TEST_SPEC oracle.sql.CLOB
  rerank: before=[161, 123, 7] after=[161, 123, 7]
  reasons: RERANK_NO_EFFECT RERANK_FLAT_SCORES SOURCE_NOT_MATCHED ANSWER_MISSING_EXPECTED_KEYWORDS ANSWER_RIGHT_SOURCE_WRONG GRAPH_BETTER_THAN_BASELINE

### ANSWER_RIGHT_SOURCE_WRONG (2)

- question: embeddingStatus 属于哪张表？
  expected: embeddingStatus RAG_DOCUMENT_CHUNKS EMBEDDING_STATUS
  missing: EMBEDDING_STATUS
  rewrite: embeddingStatus 属于哪张表？ TABLE_OR_FIELD 字段 列 COLUMN TABLE USER_TAB_COLUMNS embeddingStatus
  rerank: before=[322] after=[322]
  reasons: RERANK_NO_EFFECT RERANK_SINGLE_CANDIDATE SOURCE_NOT_MATCHED ANSWER_MISSING_EXPECTED_KEYWORDS ANSWER_RIGHT_SOURCE_WRONG
- question: 评测回答中是否应该出现 TEST_SPEC 或 oracle.sql.CLOB 这类噪声？
  expected: 不应该 噪声 TEST_SPEC oracle.sql.CLOB
  missing: 不应该 噪声 TEST_SPEC oracle.sql.CLOB
  rewrite: 评测回答中是否应该出现 TEST_SPEC 或 oracle.sql.CLOB 这类噪声？ QUALITY_OR_NOISE 噪声 异常 问题 失败 误差 命中率低 TEST_SPEC oracle.sql.CLOB
  rerank: before=[161, 123, 7] after=[161, 123, 7]
  reasons: RERANK_NO_EFFECT RERANK_FLAT_SCORES SOURCE_NOT_MATCHED ANSWER_MISSING_EXPECTED_KEYWORDS ANSWER_RIGHT_SOURCE_WRONG GRAPH_BETTER_THAN_BASELINE

### GRAPH_BETTER_THAN_BASELINE (5)

- question: RAG_ENTITY_RELATIONS 表有哪些字段？
  expected: SOURCE_ENTITY_ID TARGET_ENTITY_ID RELATION_TYPE DOCUMENT_ID CHUNK_ID EVIDENCE_TEXT CREATED_AT CONFIDENCE
  missing: 
  rewrite: RAG_ENTITY_RELATIONS 表有哪些字段？ TABLE_OR_FIELD 字段 列 COLUMN TABLE USER_TAB_COLUMNS RAG_ENTITY_RELATIONS
  rerank: before=[321] after=[321]
  reasons: RERANK_NO_EFFECT RERANK_SINGLE_CANDIDATE GRAPH_BETTER_THAN_BASELINE
- question: 图增强问答相比 baseline 会多利用什么信息？
  expected: 关系 实体 relation graph
  missing: graph
  rewrite: 图增强问答相比 baseline 会多利用什么信息？ ENTITY graph relation 关系 实体 RelationQueryService matchedRelations baseline
  rerank: before=[321] after=[321]
  reasons: RERANK_NO_EFFECT RERANK_SINGLE_CANDIDATE ANSWER_MISSING_EXPECTED_KEYWORDS GRAPH_BETTER_THAN_BASELINE
- question: RelationQueryService 的作用是什么？
  expected: RelationQueryService 关系 实体
  missing: 
  rewrite: RelationQueryService 的作用是什么？ ENTITY 作用 职责 功能 负责 调用链 RelationQueryService
  rerank: before=[321, 122, 123, 5] after=[321, 122, 123, 5]
  reasons: RERANK_NO_EFFECT RERANK_CONFIDENT_STABLE GRAPH_BETTER_THAN_BASELINE

### REWRITE_NO_EFFECT (2)

- question: /search 的排序规则是什么？
  expected: DOCUMENT_ID DESC CHUNK_INDEX ASC
  missing: 
  rewrite: /search 的排序规则是什么？ ENTITY /search
  rerank: before=[322, 161] after=[161, 322]
  reasons: REWRITE_NO_EFFECT RERANK_FLAT_SCORES
- question: QueryUnderstandingService 会识别哪些查询信息？
  expected: intent focusEntity keyword queryTerms boostTerms
  missing: 
  rewrite: QueryUnderstandingService 会识别哪些查询信息？ ENTITY QueryUnderstandingService
  rerank: before=[321] after=[321]
  reasons: REWRITE_NO_EFFECT RERANK_NO_EFFECT RERANK_SINGLE_CANDIDATE GRAPH_BETTER_THAN_BASELINE

### RERANK_FLAT_SCORES (2)

- question: /search 的排序规则是什么？
  expected: DOCUMENT_ID DESC CHUNK_INDEX ASC
  missing: 
  rewrite: /search 的排序规则是什么？ ENTITY /search
  rerank: before=[322, 161] after=[161, 322]
  reasons: REWRITE_NO_EFFECT RERANK_FLAT_SCORES
- question: 评测回答中是否应该出现 TEST_SPEC 或 oracle.sql.CLOB 这类噪声？
  expected: 不应该 噪声 TEST_SPEC oracle.sql.CLOB
  missing: 不应该 噪声 TEST_SPEC oracle.sql.CLOB
  rewrite: 评测回答中是否应该出现 TEST_SPEC 或 oracle.sql.CLOB 这类噪声？ QUALITY_OR_NOISE 噪声 异常 问题 失败 误差 命中率低 TEST_SPEC oracle.sql.CLOB
  rerank: before=[161, 123, 7] after=[161, 123, 7]
  reasons: RERANK_NO_EFFECT RERANK_FLAT_SCORES SOURCE_NOT_MATCHED ANSWER_MISSING_EXPECTED_KEYWORDS ANSWER_RIGHT_SOURCE_WRONG GRAPH_BETTER_THAN_BASELINE


## Diagnosis Reason Comparison

| Reason | Previous | Current | Delta |
| --- | --- | --- | --- |
| REWRITE_NO_EFFECT | 15 | 2 | -13 |
| NO_CANDIDATES_RECALLED | 5 | 0 | -5 |
| RERANK_NO_EFFECT | 15 | 14 | -1 |
| SOURCE_NOT_MATCHED | 6 | 3 | -3 |
| ANSWER_MISSING_EXPECTED_KEYWORDS | 13 | 10 | -3 |

## Query Rewrite Effectiveness

- total: 15
- effectiveRewriteCount: 13
- rewriteNoEffectCount: 2
- effectiveRewriteRate: 0.87
- searchQueryCount: 96
- matchedQueryCount: 107

## Recall Effectiveness

- total: 15
- noCandidatesRecalledCount: 0
- recallSuccessRate: 1.00
- fallbackUsedCount: 14
- candidateCountAfterMergeTotal: 45
- duplicateCandidateRemovedTotal: 78

## Candidate Quality Analysis

- averageCandidateCountBeforeRerank: 2.47
- averageUniqueCandidateCount: 2.47
- candidateCountDistribution: {0=0, 1=5, 2-4=8, 5+=2}
- rerankChangeReasonCounts: {SINGLE_CANDIDATE=5, CONFIDENT_STABLE=8, FLAT_SCORES=2}
- rerankNoEffectMainReason: CONFIDENT_STABLE(8)
- recallExpansionEffect: fallbackUsedCount=14, duplicateCandidateRemovedTotal=78, noCandidatesRecalledCount=0
- candidateQualitySuggestion: 排序已经稳定，下一步优先看 evidence/source 和 answer assembly。

## Noise Samples

- EVAL-015 评测回答中是否应该出现 TEST_SPEC 或 oracle.sql.CLOB 这类噪声？ | graph improved keyword hit; graph readability higher; graph noise detected; expected source not found in graph sources

## Rewrite Samples

- EVAL-001 intent=RELATION rewritten=/ask 依赖哪些核心服务？ RELATION 关系 依赖 DEPENDS_ON API_BELONGS_TO_SERVICE BELONGS_TO /ask expanded=关系 依赖 DEPENDS_ON API_BELONGS_TO_SERVICE BELONGS_TO /ask
- EVAL-002 intent=ENTITY rewritten=SearchService 的作用是什么？ ENTITY 作用 职责 功能 负责 调用链 SearchService expanded=作用 职责 功能 负责 调用链 SearchService
- EVAL-003 intent=ENTITY rewritten=ContextBuilderService 会组装哪些上下文信息？ ENTITY matchedEntities matchedRelations retrievedChunks evidenceTexts StructuredContext ContextBuilderService expanded=matchedEntities matchedRelations retrievedChunks evidenceTexts StructuredContext ContextBuilderService
- EVAL-004 intent=ENTITY rewritten=AnswerBuilderService 在 /ask 流程中负责什么？ ENTITY 作用 职责 功能 负责 调用链 AnswerBuilderService /ask expanded=作用 职责 功能 负责 调用链 AnswerBuilderService /ask
- EVAL-005 intent=TABLE_OR_FIELD rewritten=RAG_DOCUMENT_CHUNKS 表有哪些关键字段？ TABLE_OR_FIELD 字段 列 COLUMN TABLE USER_TAB_COLUMNS RAG_DOCUMENT_CHUNKS expanded=字段 列 COLUMN TABLE USER_TAB_COLUMNS RAG_DOCUMENT_CHUNKS

## Rewrite Effective Samples

- EVAL-001 rewritten=/ask 依赖哪些核心服务？ RELATION 关系 依赖 DEPENDS_ON API_BELONGS_TO_SERVICE BELONGS_TO /ask expanded=关系 依赖 DEPENDS_ON API_BELONGS_TO_SERVICE BELONGS_TO /ask
- EVAL-002 rewritten=SearchService 的作用是什么？ ENTITY 作用 职责 功能 负责 调用链 SearchService expanded=作用 职责 功能 负责 调用链 SearchService
- EVAL-003 rewritten=ContextBuilderService 会组装哪些上下文信息？ ENTITY matchedEntities matchedRelations retrievedChunks evidenceTexts StructuredContext ContextBuilderService expanded=matchedEntities matchedRelations retrievedChunks evidenceTexts StructuredContext ContextBuilderService
- EVAL-004 rewritten=AnswerBuilderService 在 /ask 流程中负责什么？ ENTITY 作用 职责 功能 负责 调用链 AnswerBuilderService /ask expanded=作用 职责 功能 负责 调用链 AnswerBuilderService /ask
- EVAL-005 rewritten=RAG_DOCUMENT_CHUNKS 表有哪些关键字段？ TABLE_OR_FIELD 字段 列 COLUMN TABLE USER_TAB_COLUMNS RAG_DOCUMENT_CHUNKS expanded=字段 列 COLUMN TABLE USER_TAB_COLUMNS RAG_DOCUMENT_CHUNKS

## Score Details

- EVAL-001 chunk=321 keyword=0.3333 keywordCoverage=0.3333 entity=1.0 entityCoverage=1.0 relation=0.8667 intentMatch=1.0 source=0.75 sourceType=RELATION_EVIDENCE noisePenalty=0.0 final=0.775
- EVAL-002 chunk=321 keyword=0.3333 keywordCoverage=0.3333 entity=1.0 entityCoverage=1.0 relation=0.0 intentMatch=1.0 source=1.0 sourceType=DOCUMENT_CHUNK noisePenalty=0.0 final=0.5833
- EVAL-003 chunk=321 keyword=1.0 keywordCoverage=1.0 entity=1.0 entityCoverage=1.0 relation=0.0 intentMatch=1.0 source=1.0 sourceType=DOCUMENT_CHUNK noisePenalty=0.0 final=0.85
- EVAL-004 chunk=321 keyword=0.4286 keywordCoverage=0.4286 entity=1.0 entityCoverage=1.0 relation=0.0 intentMatch=1.0 source=1.0 sourceType=DOCUMENT_CHUNK noisePenalty=0.0 final=0.6214
- EVAL-005 chunk=322 keyword=0.5 keywordCoverage=0.5 entity=1.0 entityCoverage=1.0 relation=0.0 intentMatch=1.0 source=0.98 sourceType=DOCUMENT_CHUNK noisePenalty=0.0 final=0.648

## Rerank Before After

- EVAL-001 before=[321] after=[321]
- EVAL-002 before=[321, 161, 122, 123, 5] after=[321, 161, 122, 123, 5]
- EVAL-003 before=[321, 161] after=[321, 161]
- EVAL-004 before=[321, 161, 122, 123, 5] after=[321, 161, 122, 123, 5]
- EVAL-005 before=[322, 8] after=[322, 8]

## Rerank Diagnostics

- orderUnchangedCount: 0
- flatScoresCount: 2
- rerankChangedCount: 1
- averageTopScoreGap: 0.28
- sortingChangedCases:
  - EVAL-008 before=[322, 161] after=[161, 322] topScoreGap=0.002 reason=FLAT_SCORES topChunk=161 final=0.85 intent=1.0 entityCoverage=1.0 noisePenalty=0.0

## Rerank Effective Samples

- EVAL-008 before=[322, 161] after=[161, 322]

## Graph Better Samples

- EVAL-007 RAG_ENTITY_RELATIONS 表有哪些字段？ | graph improved keyword hit
- EVAL-011 图增强问答相比 baseline 会多利用什么信息？ | graph improved keyword hit; graph readability higher
- EVAL-012 RelationQueryService 的作用是什么？ | graph improved keyword hit; graph readability higher

## Graph Worse Samples

- none

## Failure Case Analysis

- EVAL-006 embeddingStatus 属于哪张表？: RERANK_NO_EFFECT RERANK_SINGLE_CANDIDATE SOURCE_NOT_MATCHED ANSWER_MISSING_EXPECTED_KEYWORDS ANSWER_RIGHT_SOURCE_WRONG; missing=EMBEDDING_STATUS; rewrite=embeddingStatus 属于哪张表？ TABLE_OR_FIELD 字段 列 COLUMN TABLE USER_TAB_COLUMNS embeddingStatus; rerank before=[322] after=[322]
- EVAL-014 回答里应该如何呈现来源证据？: RERANK_NO_EFFECT RERANK_CONFIDENT_STABLE SOURCE_NOT_MATCHED ANSWER_MISSING_EXPECTED_KEYWORDS; missing=sources evidence; rewrite=回答里应该如何呈现来源证据？ SOURCE source 依据 来源 证据 文档 sources evidence; rerank before=[5, 161, 322] after=[5, 161, 322]
- EVAL-015 评测回答中是否应该出现 TEST_SPEC 或 oracle.sql.CLOB 这类噪声？: RERANK_NO_EFFECT RERANK_FLAT_SCORES SOURCE_NOT_MATCHED ANSWER_MISSING_EXPECTED_KEYWORDS ANSWER_RIGHT_SOURCE_WRONG GRAPH_BETTER_THAN_BASELINE; missing=不应该 噪声 TEST_SPEC oracle.sql.CLOB; rewrite=评测回答中是否应该出现 TEST_SPEC 或 oracle.sql.CLOB 这类噪声？ QUALITY_OR_NOISE 噪声 异常 问题 失败 误差 命中率低 TEST_SPEC oracle.sql.CLOB; rerank before=[161, 123, 7] after=[161, 123, 7]

## Next Steps

- Main priority: inspect evidence/source and answer assembly, because rerank now produces confident stable top candidates.
- Improve rewrite rules for cases where rewrittenQuery only repeats the original terms.
- Improve evidence/source matching so graph answers expose the expected source terms.
- Inspect answer assembly for cases where candidates exist but expected keywords are not surfaced.
- Keep the current fixed eval as a regression gate before changing retrieval algorithms.
