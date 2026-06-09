package com.example.rag.service;

import com.example.rag.dto.AgentPlan;
import com.example.rag.dto.AgentPlanningInput;

public interface AgentPlanner {

    AgentPlan plan(AgentPlanningInput input);
}
