package com.example.rag.service.impl;

import com.example.rag.config.AnswerGenerationProperties;
import com.example.rag.dto.AgentToolInput;
import com.example.rag.dto.AgentToolResult;
import com.example.rag.dto.AskDebugInfo;
import com.example.rag.dto.AskResponse;
import com.example.rag.dto.LlmAnswerRequest;
import com.example.rag.dto.LlmAnswerResult;
import com.example.rag.dto.QueryRewriteResult;
import com.example.rag.dto.RerankResult;
import com.example.rag.dto.SqlTableColumnResult;
import com.example.rag.enums.ToolType;
import com.example.rag.service.AgentTool;
import com.example.rag.service.LlmAnswerClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;

@Service
public class AnswerGenerateTool implements AgentTool {

    private final AgentAnswerAssembler agentAnswerAssembler;
    private final RuleBasedAnswerClient ruleBasedAnswerClient;
    private final LangChain4jAnswerClient langChain4jAnswerClient;
    private final AnswerGenerationProperties answerGenerationProperties;

    public AnswerGenerateTool(AgentAnswerAssembler agentAnswerAssembler,
                              RuleBasedAnswerClient ruleBasedAnswerClient,
                              LangChain4jAnswerClient langChain4jAnswerClient,
                              AnswerGenerationProperties answerGenerationProperties) {
        this.agentAnswerAssembler = agentAnswerAssembler;
        this.ruleBasedAnswerClient = ruleBasedAnswerClient;
        this.langChain4jAnswerClient = langChain4jAnswerClient;
        this.answerGenerationProperties = answerGenerationProperties;
    }

    @Override
    public ToolType type() {
        return ToolType.ANSWER_GENERATE;
    }

    @Override
    public AgentToolResult execute(AgentToolInput input) {
        long start = System.currentTimeMillis();
        AgentToolResult result = new AgentToolResult();
        result.setToolType(type());
        try {
            AskResponse response;
            if (input != null && input.getContext() instanceof SqlTableColumnResult) {
                response = agentAnswerAssembler.buildSqlMetadataResponse(
                        input.getQuestion(),
                        (SqlTableColumnResult) input.getContext()
                );
                return successResult(result, response, input, start, "answer generated from sql metadata");
            } else if (input != null && input.getRelationHits() != null && !input.getRelationHits().isEmpty()) {
                response = agentAnswerAssembler.buildRelationResponse(
                        input.getQuestion(),
                        input.getRelationHits()
                );
                return successResult(result, response, input, start, "answer generated from relation context");
            } else if (input != null && input.getChunkHits() != null && !input.getChunkHits().isEmpty()) {
                response = agentAnswerAssembler.buildChunkResponse(
                        input.getQuestion(),
                        input.getChunkHits()
                );
                return successResult(result, response, input, start, "answer generated from chunk context");
            } else {
                response = agentAnswerAssembler.buildNoAnswerResponse(
                        input == null ? null : input.getQuestion(),
                        "BASELINE",
                        "BASELINE"
                );
                return successResult(result, response, input, start, "no chunk or relation context available for answer generation");
            }
        } catch (RuntimeException ex) {
            result.setSuccess(false);
            result.setErrorMessage(StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : ex.getClass().getSimpleName());
            result.setSummary("answer generation failed");
            result.setLatencyMs(System.currentTimeMillis() - start);
            return result;
        }
    }

    private AgentToolResult successResult(AgentToolResult result,
                                          AskResponse response,
                                          AgentToolInput input,
                                          long start,
                                          String summary) {
        LlmAnswerResult answerResult = generateAnswer(input, response);
        if (StringUtils.hasText(answerResult.getAnswer())) {
            response.setAnswer(answerResult.getAnswer());
        }
        applyRetrievalDebug(response, input);
        applyAnswerDebug(response, answerResult);
        result.setSuccess(true);
        result.setData(response);
        result.setSummary(summary + "; answerProvider=" + answerResult.getProvider()
                + "; fallbackUsed=" + answerResult.isFallbackUsed());
        result.setLatencyMs(System.currentTimeMillis() - start);
        result.setAnswerProvider(answerResult.getProvider());
        result.setAnswerModelName(answerResult.getModelName());
        result.setAnswerFallbackUsed(answerResult.isFallbackUsed());
        if (StringUtils.hasText(answerResult.getErrorMessage())) {
            result.setErrorMessage(answerResult.getErrorMessage());
        }
        return result;
    }

    private LlmAnswerResult generateAnswer(AgentToolInput input, AskResponse response) {
        LlmAnswerClient client = shouldUseLangChain4j() ? langChain4jAnswerClient : ruleBasedAnswerClient;
        return client.generate(buildLlmAnswerRequest(input, response));
    }

    private boolean shouldUseLangChain4j() {
        String mode = answerGenerationProperties == null ? null : answerGenerationProperties.getMode();
        return "langchain4j".equalsIgnoreCase(mode)
                && answerGenerationProperties.getLangchain4j() != null
                && answerGenerationProperties.getLangchain4j().isEnabled();
    }

    private LlmAnswerRequest buildLlmAnswerRequest(AgentToolInput input, AskResponse response) {
        LlmAnswerRequest request = new LlmAnswerRequest();
        request.setQuestion(input == null ? null : input.getQuestion());
        request.setTopChunks(input == null || input.getChunkHits() == null ? new ArrayList<>() : input.getChunkHits());
        request.setRelationEvidence(input == null || input.getRelationHits() == null ? new ArrayList<>() : input.getRelationHits());
        request.setSources(response == null || response.getSources() == null ? new ArrayList<>() : response.getSources());
        request.setStructuredContext(response == null ? null : response.getStructuredContext());
        request.setRuleBasedAnswer(response == null ? null : response.getAnswer());
        request.setTraceContext(buildTraceContext(input));
        return request;
    }

    private String buildTraceContext(AgentToolInput input) {
        if (input == null) {
            return "";
        }
        return "matchedQueries=" + input.getMatchedQueries()
                + ", recallFallbackUsed=" + input.isRecallFallbackUsed()
                + ", candidateCountAfterMerge=" + input.getCandidateCountAfterMerge();
    }

    private void applyRetrievalDebug(AskResponse response, AgentToolInput input) {
        if (response == null || input == null) {
            return;
        }
        if (response.getDebug() == null) {
            response.setDebug(new AskDebugInfo());
        }
        QueryRewriteResult rewrite = input.getRewriteResult();
        if (rewrite != null) {
            response.getDebug().setRewrittenQuery(rewrite.getRewrittenQuery());
            response.getDebug().setExpandedKeywords(rewrite.getExpandedKeywords());
            response.getDebug().setSearchQueries(rewrite.getSearchQueries());
            response.getDebug().setDetectedIntent(rewrite.getDetectedIntent());
            response.getDebug().setDetectedEntities(rewrite.getDetectedEntities());
        }
        response.getDebug().setMatchedQueries(input.getMatchedQueries());
        response.getDebug().setRecallFallbackUsed(input.isRecallFallbackUsed());
        response.getDebug().setCandidateCountByQuery(input.getCandidateCountByQuery());
        response.getDebug().setCandidateCountAfterMerge(input.getCandidateCountAfterMerge());
        response.getDebug().setDuplicateCandidateRemovedCount(input.getDuplicateCandidateRemovedCount());
        RerankResult rerank = input.getRerankResult();
        if (rerank != null) {
            response.getDebug().setTopScoreDetails(rerank.getTopScoreDetails());
            response.getDebug().setRerankBeforeChunkIds(rerank.getBeforeChunkIds());
            response.getDebug().setRerankAfterChunkIds(rerank.getAfterChunkIds());
            response.getDebug().setUniqueCandidateCount(rerank.getUniqueCandidateCount());
            response.getDebug().setCandidateScoreSpread(rerank.getCandidateScoreSpread());
            response.getDebug().setTop1Score(rerank.getTop1Score());
            response.getDebug().setTop2Score(rerank.getTop2Score());
            response.getDebug().setTopScoreGap(rerank.getTopScoreGap());
            response.getDebug().setRerankChanged(rerank.isRerankChanged());
            response.getDebug().setRerankChangeReason(rerank.getRerankChangeReason());
        }
    }

    private void applyAnswerDebug(AskResponse response, LlmAnswerResult answerResult) {
        if (response == null || answerResult == null) {
            return;
        }
        if (response.getDebug() == null) {
            response.setDebug(new AskDebugInfo());
        }
        response.getDebug().setAnswerProvider(answerResult.getProvider());
        response.getDebug().setAnswerModelName(answerResult.getModelName());
        response.getDebug().setAnswerLatencyMs(answerResult.getLatencyMs());
        response.getDebug().setAnswerFallbackUsed(answerResult.isFallbackUsed());
        response.getDebug().setAnswerErrorMessage(answerResult.getErrorMessage());
    }
}
