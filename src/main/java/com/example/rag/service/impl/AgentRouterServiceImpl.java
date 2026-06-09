package com.example.rag.service.impl;

import com.example.rag.dto.AgentPlan;
import com.example.rag.dto.AgentPlanningInput;
import com.example.rag.dto.AgentRouteDecision;
import com.example.rag.enums.QuestionType;
import com.example.rag.service.AgentPlanner;
import com.example.rag.service.AgentRouterService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AgentRouterServiceImpl implements AgentRouterService {

    private final AgentPlanner ruleBasedAgentPlanner;
    private final AgentPlanner llmAgentPlannerStub;
    private final String plannerType;

    public AgentRouterServiceImpl(RuleBasedAgentPlanner ruleBasedAgentPlanner,
                                  LlmAgentPlannerStub llmAgentPlannerStub,
                                  @Value("${agent.planner.type:rule}") String plannerType) {
        this.ruleBasedAgentPlanner = ruleBasedAgentPlanner;
        this.llmAgentPlannerStub = llmAgentPlannerStub;
        this.plannerType = plannerType;
    }

    @Override
    public AgentRouteDecision route(String question, QuestionType questionType) {
        AgentPlanningInput input = new AgentPlanningInput();
        input.setQuestion(question);
        input.setQuestionType(questionType);

        AgentPlan plan = selectPlanner().plan(input);

        AgentRouteDecision decision = new AgentRouteDecision();
        decision.setQuestionType(plan.getQuestionType() == null ? questionType : plan.getQuestionType());
        decision.setSelectedTools(plan.getSelectedTools());
        decision.setReason(plan.getReason());
        return decision;
    }

    private AgentPlanner selectPlanner() {
        if ("llm".equalsIgnoreCase(plannerType)) {
            return llmAgentPlannerStub;
        }
        return ruleBasedAgentPlanner;
    }
}
