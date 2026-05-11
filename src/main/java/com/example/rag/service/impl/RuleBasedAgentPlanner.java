package com.example.rag.service.impl;

import com.example.rag.dto.AgentPlan;
import com.example.rag.dto.AgentPlanningInput;
import com.example.rag.enums.QuestionType;
import com.example.rag.enums.ToolType;
import com.example.rag.service.AgentPlanner;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@Primary
public class RuleBasedAgentPlanner implements AgentPlanner {

    @Override
    public AgentPlan plan(AgentPlanningInput input) {
        QuestionType questionType = input == null ? QuestionType.UNKNOWN : input.getQuestionType();
        String question = input == null ? null : input.getQuestion();

        AgentPlan plan = new AgentPlan();
        plan.setQuestionType(questionType);
        plan.setSelectedTools(buildSelectedTools(question, questionType));
        plan.setReason(buildReason(question, questionType));
        plan.setMaxSteps(buildMaxSteps(plan.getSelectedTools()));
        return plan;
    }

    private List<ToolType> buildSelectedTools(String question, QuestionType questionType) {
        List<ToolType> tools = new ArrayList<>();
        if (questionType == QuestionType.RELATION) {
            if (isTableFieldQuestion(question)) {
                tools.add(ToolType.GRAPH_SEARCH);
                tools.add(ToolType.SQL_SEARCH);
                tools.add(ToolType.ANSWER_GENERATE);
                return tools;
            }
            tools.add(ToolType.GRAPH_SEARCH);
            tools.add(ToolType.CHUNK_SEARCH);
            tools.add(ToolType.ANSWER_GENERATE);
            return tools;
        }
        tools.add(ToolType.CHUNK_SEARCH);
        tools.add(ToolType.ANSWER_GENERATE);
        return tools;
    }

    private String buildReason(String question, QuestionType questionType) {
        if (questionType == QuestionType.RELATION) {
            if (isTableFieldQuestion(question)) {
                return "table field question uses graph search with sql metadata fallback";
            }
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

    private Integer buildMaxSteps(List<ToolType> selectedTools) {
        return selectedTools == null ? 0 : selectedTools.size();
    }

    private boolean isTableFieldQuestion(String question) {
        if (question == null) {
            return false;
        }
        String normalized = question.toLowerCase(Locale.ROOT);
        return normalized.contains("\u8868\u6709\u54ea\u4e9b\u5b57\u6bb5")
                || normalized.contains("\u8868\u5305\u542b\u54ea\u4e9b\u5b57\u6bb5")
                || normalized.contains("\u6709\u54ea\u4e9b\u5217")
                || normalized.contains("\u8868\u5b57\u6bb5")
                || normalized.contains("\u5b57\u6bb5\u662f\u4ec0\u4e48");
    }
}
