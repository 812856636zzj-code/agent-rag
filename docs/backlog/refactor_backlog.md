# RAG 项目重构 Backlog

## P0 立即做

1. RetrievalService / SearchOrchestrator 统一检索编排
2. 规则常量集中治理
3. UploadController 瘦身
4. RELATED_TO 降权

## P1 稳定后做

1. service/impl 目录统一
2. repository 命名统一
3. ContextBuilderService 拆 EvidenceExtractor / GraphContextBuilder

## P2 最后做

1. Document -> RagDocument
2. DocumentChunk -> RagDocumentChunk
3. DocumentEntityLink -> RagDocumentEntityLink
