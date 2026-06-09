package com.example.rag.service.impl;

import com.example.rag.dto.AgentToolInput;
import com.example.rag.dto.AgentToolResult;
import com.example.rag.dto.AskDebugInfo;
import com.example.rag.dto.AskResponse;
import com.example.rag.dto.QueryRewriteResult;
import com.example.rag.dto.RerankResult;
import com.example.rag.dto.SqlTableColumnResult;
import com.example.rag.enums.ToolType;
import com.example.rag.service.AgentTool;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AnswerGenerateTool implements AgentTool {

    private final AgentAnswerAssembler agentAnswerAssembler;

    public AnswerGenerateTool(AgentAnswerAssembler agentAnswerAssembler) {
        this.agentAnswerAssembler = agentAnswerAssembler;
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
            if (input != null && input.getContext() instanceof SqlTableColumnResult) {
                AskResponse response = agentAnswerAssembler.buildSqlMetadataResponse(
                        input.getQuestion(),
                        (SqlTableColumnResult) input.getContext()
                );
                applyRetrievalDebug(response, input);
                result.setSuccess(true);
                result.setData(response);
                result.setSummary("answer generated from sql metadata");
                result.setLatencyMs(System.currentTimeMillis() - start);
                return result;
            }

            if (input != null && input.getRelationHits() != null && !input.getRelationHits().isEmpty()) {
                AskResponse response = agentAnswerAssembler.buildRelationResponse(
                        input.getQuestion(),
                        input.getRelationHits()
                );
                applyRetrievalDebug(response, input);
                result.setSuccess(true);
                result.setData(response);
                result.setSummary("answer generated from relation context");
                result.setLatencyMs(System.currentTimeMillis() - start);
                return result;
            }

            if (input != null && input.getChunkHits() != null && !input.getChunkHits().isEmpty()) {
                AskResponse response = agentAnswerAssembler.buildChunkResponse(
                        input.getQuestion(),
                        input.getChunkHits()
                );
                applyRetrievalDebug(response, input);
                result.setSuccess(true);
                result.setData(response);
                result.setSummary("answer generated from chunk context");
                result.setLatencyMs(System.currentTimeMillis() - start);
                return result;
            }

            result.setSuccess(true);
            AskResponse response = agentAnswerAssembler.buildNoAnswerResponse(
                    input == null ? null : input.getQuestion(),
                    "BASELINE",
                    "BASELINE"
            );
            applyRetrievalDebug(response, input);
            result.setData(response);
            result.setSummary("no chunk or relation context available for answer generation");
            result.setLatencyMs(System.currentTimeMillis() - start);
            return result;
        } catch (RuntimeException ex) {
            result.setSuccess(false);
            result.setErrorMessage(StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : ex.getClass().getSimpleName());
            result.setSummary("answer generation failed");
            result.setLatencyMs(System.currentTimeMillis() - start);
            return result;
        }
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
}
