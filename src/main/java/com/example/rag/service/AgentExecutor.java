package com.example.rag.service;

import com.example.rag.dto.AgentRouteDecision;
import com.example.rag.dto.AskResponse;

public interface AgentExecutor {

    AskResponse execute(String question, AgentRouteDecision routeDecision);
}
