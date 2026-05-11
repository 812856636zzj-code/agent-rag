package com.example.rag.service.impl;

import com.example.rag.dto.AgentPlan;
import com.example.rag.dto.AgentPlanningInput;
import com.example.rag.service.AgentPlanner;
import org.springframework.stereotype.Service;

@Service
public class LlmAgentPlannerStub implements AgentPlanner {

    @Override
    public AgentPlan plan(AgentPlanningInput input) {
        AgentPlan plan = new AgentPlan();
        plan.setQuestionType(input == null ? null : input.getQuestionType());
        plan.setReason("llm planner stub is unsupported in this stage; fallback to rule-based planner");
        plan.setMaxSteps(0);
        return plan;
    }
}
