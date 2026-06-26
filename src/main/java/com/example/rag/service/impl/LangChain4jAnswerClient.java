package com.example.rag.service.impl;

import com.example.rag.config.AnswerGenerationProperties;
import com.example.rag.dto.ChunkHit;
import com.example.rag.dto.LlmAnswerRequest;
import com.example.rag.dto.LlmAnswerResult;
import com.example.rag.dto.RelationHit;
import com.example.rag.dto.SourceItem;
import com.example.rag.service.LlmAnswerClient;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;

@Service
public class LangChain4jAnswerClient implements LlmAnswerClient {

    private final AnswerGenerationProperties properties;
    private final RuleBasedAnswerClient ruleBasedAnswerClient;

    public LangChain4jAnswerClient(AnswerGenerationProperties properties,
                                   RuleBasedAnswerClient ruleBasedAnswerClient) {
        this.properties = properties;
        this.ruleBasedAnswerClient = ruleBasedAnswerClient;
    }

    @Override
    public LlmAnswerResult generate(LlmAnswerRequest request) {
        long start = System.currentTimeMillis();
        AnswerGenerationProperties.LangChain4j config = properties.getLangchain4j();
        if (config == null || !config.isEnabled()) {
            return fallback(request, start, "langchain4j disabled");
        }
        if (!StringUtils.hasText(config.getApiKey())) {
            return fallback(request, start, "langchain4j apiKey is blank");
        }
        try {
            OpenAiChatModel model = OpenAiChatModel.builder()
                    .apiKey(config.getApiKey())
                    .modelName(config.getModelName())
                    .timeout(Duration.ofMillis(Math.max(1000L, config.getTimeoutMs())))
                    .maxTokens(config.getMaxTokens())
                    .build();
            String answer = model.generate(buildPrompt(request));
            LlmAnswerResult result = new LlmAnswerResult();
            result.setAnswer(answer);
            result.setProvider("LANGCHAIN4J");
            result.setModelName(config.getModelName());
            result.setLatencyMs(System.currentTimeMillis() - start);
            result.setFallbackUsed(false);
            return result;
        } catch (RuntimeException ex) {
            return fallback(request, start, StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : ex.getClass().getSimpleName());
        }
    }

    private LlmAnswerResult fallback(LlmAnswerRequest request, long start, String errorMessage) {
        if (properties.getLangchain4j() != null && properties.getLangchain4j().isFallbackEnabled()) {
            LlmAnswerResult result = ruleBasedAnswerClient.generate(request);
            result.setProvider("RULE");
            result.setModelName("rule-based-assembler");
            result.setLatencyMs(System.currentTimeMillis() - start);
            result.setFallbackUsed(true);
            result.setErrorMessage(errorMessage);
            return result;
        }
        LlmAnswerResult result = new LlmAnswerResult();
        result.setAnswer(request == null ? "" : request.getRuleBasedAnswer());
        result.setProvider("LANGCHAIN4J");
        result.setModelName(properties.getLangchain4j() == null ? "" : properties.getLangchain4j().getModelName());
        result.setLatencyMs(System.currentTimeMillis() - start);
        result.setFallbackUsed(false);
        result.setErrorMessage(errorMessage);
        return result;
    }

    private String buildPrompt(LlmAnswerRequest request) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是企业知识库问答助手。必须严格遵守：\n");
        prompt.append("1. 只能基于给定 chunks、relation evidence 和 structured context 回答。\n");
        prompt.append("2. 不允许编造 sources、文档名、表名、字段名或关系。\n");
        prompt.append("3. sources 由后端结构化返回，你不要自己生成或改写 source 列表。\n");
        prompt.append("4. 如果证据不足，回答：未检索到足够相关的资料，暂时无法基于知识库回答该问题。\n");
        prompt.append("5. 回答保持简洁，优先给结论，再列证据摘要。\n\n");
        prompt.append("问题：").append(request == null ? "" : request.getQuestion()).append("\n\n");
        prompt.append("Structured Context:\n").append(request == null ? "" : safe(request.getStructuredContext())).append("\n\n");
        prompt.append("Chunks:\n").append(buildChunkContext(request == null ? null : request.getTopChunks())).append("\n\n");
        prompt.append("Relation Evidence:\n").append(buildRelationContext(request == null ? null : request.getRelationEvidence())).append("\n\n");
        prompt.append("Backend Sources Preview:\n").append(buildSourceContext(request == null ? null : request.getSources())).append("\n");
        return prompt.toString();
    }

    private String buildChunkContext(List<ChunkHit> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        int limit = Math.min(5, chunks.size());
        for (int i = 0; i < limit; i++) {
            ChunkHit chunk = chunks.get(i);
            builder.append(i + 1)
                    .append(". chunkId=").append(chunk.getChunkId())
                    .append(", documentId=").append(chunk.getDocumentId())
                    .append(", content=").append(safe(chunk.getContent()))
                    .append("\n");
        }
        return builder.toString();
    }

    private String buildRelationContext(List<RelationHit> relationHits) {
        if (relationHits == null || relationHits.isEmpty()) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        int limit = Math.min(5, relationHits.size());
        for (int i = 0; i < limit; i++) {
            RelationHit hit = relationHits.get(i);
            builder.append(i + 1)
                    .append(". ").append(safe(hit.getSourceEntityName()))
                    .append(" ").append(safe(hit.getRelationType()))
                    .append(" ").append(safe(hit.getTargetEntityName()))
                    .append(", evidence=").append(safe(hit.getEvidenceText()))
                    .append("\n");
        }
        return builder.toString();
    }

    private String buildSourceContext(List<SourceItem> sources) {
        if (sources == null || sources.isEmpty()) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        int limit = Math.min(5, sources.size());
        for (int i = 0; i < limit; i++) {
            SourceItem source = sources.get(i);
            builder.append(i + 1)
                    .append(". sourceType=").append(safe(source.getSourceType()))
                    .append(", documentName=").append(safe(source.getDocumentName()))
                    .append(", chunkId=").append(source.getChunkId())
                    .append("\n");
        }
        return builder.toString();
    }

    private String safe(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.length() > 1200 ? value.substring(0, 1200) : value;
    }
}
