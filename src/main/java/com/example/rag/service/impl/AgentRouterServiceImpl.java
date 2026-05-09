package com.example.rag.service.impl;

import com.example.rag.dto.AgentRouteDecision;
import com.example.rag.enums.QuestionType;
import com.example.rag.enums.ToolType;
import com.example.rag.service.AgentRouterService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AgentRouterServiceImpl implements AgentRouterService {

    @Override
    public AgentRouteDecision route(String question, QuestionType questionType) {
        AgentRouteDecision decision = new AgentRouteDecision();
        decision.setQuestionType(questionType);
        decision.setSelectedTools(buildSelectedTools(questionType));
        decision.setReason(buildReason(questionType));
        return decision;
    }

    private List<ToolType> buildSelectedTools(QuestionType questionType) {
        List<ToolType> tools = new ArrayList<>();
        if (questionType == QuestionType.RELATION) {
            tools.add(ToolType.GRAPH_SEARCH);
            tools.add(ToolType.CHUNK_SEARCH);
            tools.add(ToolType.ANSWER_GENERATE);
            return tools;
        }

        tools.add(ToolType.CHUNK_SEARCH);
        tools.add(ToolType.ANSWER_GENERATE);
        return tools;
    }

    private String buildReason(QuestionType questionType) {
        if (questionType == QuestionType.RELATION) {
            return "relation question uses graph-first retrieval with chunk fallback";
        }
        if (questionType == QuestionType.DEFINITION) {
            return "definition question uses chunk retrieval and answer generation";
        }
        if (questionType == QuestionType.PROCEDURE) {
            return "procedure question uses chunk retrieval and answer generation";
        }
        if (questionType == QuestionType.GENERAL) {
            return "general question uses chunk retrieval and answer generation";
        }
        return "unknown question uses chunk retrieval by default";
    }
}
