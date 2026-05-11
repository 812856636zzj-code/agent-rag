package com.example.rag.service.impl;

import com.example.rag.dto.AgentToolInput;
import com.example.rag.dto.AgentToolResult;
import com.example.rag.dto.AskResponse;
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
}
