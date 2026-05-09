# Day 7 Eval Result

| 编号 | 分类 | 问题 | 期望答案 | Baseline命中 | Graph版命中 | Baseline可读性 | Graph版可读性 | 噪声情况 | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| D7-001 | 依赖类 | /ask 依赖哪些已有能力？ | /ask 依赖 SearchService、ContextBuilderService、AnswerBuilderService，并在 Day 6 中结合 QueryUnderstandingService、RelationQueryService 和 hybrid retrieval 生成图增强问答结果。 | 是 | 是 | 4 | 5 | 无 | graph readability higher |
| D7-002 | 依赖类 | PDF 导入依赖什么库？ | PDF 导入依赖 PDFBox，系统通过 PDFBox 读取 PDF 内容，再进入上传、切分和后续实体抽取流程。 | 是 | 是 | 4 | 5 | 无 | graph readability higher |
| D7-003 | 归属类 | embeddingStatus 属于哪张表？ | embeddingStatus 属于 RAG_DOCUMENT_CHUNKS 表，在数据库中对应 EMBEDDING_STATUS 字段，用于表示 chunk 的处理状态。 | 是 | 是 | 4 | 5 | 无 | graph readability higher |
| D7-004 | 检索规则类 | /search 的排序规则是什么？ | /search 通过关键词匹配定位 chunk，典型排序规则是 DOCUMENT_ID DESC、CHUNK_INDEX ASC；在 Oracle 检索里还可能结合 dbms_lob.instr 做内容匹配。 | 是 | 是 | 4 | 5 | 无 | graph readability higher |
| D7-005 | 简单定义类 | chunk 大小在哪里定义？ | chunk 大小通常定义在 DocumentChunkService 中的 CHUNK_SIZE 常量，当前值一般为 500，并在 saveChunks 或切分逻辑中使用。 | 是 | 是 | 4 | 5 | 无 | graph readability higher |
| D7-006 | 配置关联类 | spring.datasource.url 配置在哪里？ | spring.datasource.url 通常配置在 application.yml 或 application.properties 中，用于定义数据库连接地址。 | 否 | 否 | 3 | 3 | 无 |  |
| D7-007 | 配置关联类 | server.port 配置在哪里？ | server.port 一般配置在 application.yml 或 application.properties 中，用于指定服务启动端口。 | 否 | 否 | 3 | 3 | 无 |  |
| D7-008 | 依赖类 | PDFBox 和哪些服务有关？ | PDFBox 主要与 UploadService、DocumentChunkService 等服务有关，这些服务会使用它解析 PDF 内容并进入文档上传或切分流程。 | 是 | 是 | 4 | 5 | 无 | graph readability higher |
| D7-009 | 简单定义类 | RAG_DOCUMENT_CHUNKS 里有哪些关键字段？ | RAG_DOCUMENT_CHUNKS 的关键字段包括 ID、DOCUMENT_ID、CHUNK_INDEX、CONTENT、TOKEN_COUNT、CREATE_TIME、EMBEDDING_STATUS，用于保存分块内容及处理状态。 | 是 | 是 | 4 | 5 | 无 | graph readability higher |
| D7-010 | 简单定义类 | SearchService 的作用是什么？ | SearchService 负责统一检索入口，支持 keyword search、entity search 和 hybrid search，为 /search 与 /ask 提供 chunk 召回结果。 | 是 | 是 | 4 | 5 | 无 | graph readability higher |
| D7-011 | 简单定义类 | ContextBuilderService 的作用是什么？ | ContextBuilderService 用于把召回到的 chunk、实体和关系整理成结构化上下文，供 /ask 的回答生成阶段使用。 | 是 | 是 | 4 | 5 | 无 | graph readability higher |
| D7-012 | 简单定义类 | AnswerBuilderService 的作用是什么？ | AnswerBuilderService 基于结构化上下文组织最终回答，通常先给结论，再给实体或关系，再补充证据摘要。 | 是 | 是 | 4 | 5 | 无 | graph readability higher |
| D7-013 | 简单定义类 | RelationQueryService 是做什么的？ | RelationQueryService 用于按实体查询轻关系层中的相邻实体和关系证据，为 graph 增强问答补充 relation hits。 | 否 | 否 | 3 | 3 | 无 |  |
| D7-014 | 归属类 | RAG_ENTITY_RELATIONS 存什么？ | RAG_ENTITY_RELATIONS 存储实体之间的轻量关系，包括 sourceEntity、targetEntity、relationType、documentId、chunkId 和 evidenceText 等信息。 | 否 | 是 | 4 | 5 | 无 | graph improved hit; graph readability higher |
| D7-015 | 依赖类 | /upload 做了什么？ | /upload 负责接收文档、保存本地文件与文档元数据、做重复上传检测、切分 chunk，并触发实体抽取与后续关系构建流程。 | 否 | 是 | 3 | 3 | 无 | graph improved hit |
| D7-016 | 检索规则类 | dbms_lob.instr 用在哪里？ | dbms_lob.instr 用在 Oracle CLOB 内容检索中，通常用于 /search 的关键词匹配，避免直接对大文本字段做不合适的比较。 | 否 | 是 | 4 | 5 | 无 | graph improved hit; graph readability higher |
| D7-017 | 异常定位类 | 为什么重复上传要拦截？ | 重复上传需要拦截，因为相同文档如果重复入库，会重复写入文档、chunk、实体和实体关联，影响检索质量，也增加存储和调试噪音。 | 是 | 否 | 4 | 5 | 无 | graph readability higher |
| D7-018 | 异常定位类 | 如果 /ask 没有命中 chunk，可能是什么原因？ | /ask 没有命中 chunk，常见原因包括 keyword 提取失败、实体抽取失败、文档类型被过滤、knowledge 语料不足，或 relation 与 chunk 检索都没有召回有效证据。 | 是 | 是 | 4 | 5 | 无 | graph readability higher |

## 总结

1. 哪类题提升最大
- 覆盖 8 题，Graph 相比 Baseline 新增命中 2 题，可读性提升 5 题。

2. 哪类题提升有限
- 覆盖 6 题，Graph 相比 Baseline 新增命中 0 题，可读性提升 5 题。

3. 哪类题还会翻车
- 覆盖 2 题，Graph 相比 Baseline 新增命中 0 题，可读性提升 2 题。

4. 当前主要问题
- 同 chunk 共现导致 RELATED_TO 噪声偏大。
- relation evidence 仍需 sentence 级优化，尤其是多主题 chunk。
- 异常定位缺少真实运行日志和因果链路，Graph 版本也容易回答泛化。
