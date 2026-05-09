package com.example.rag.service;

import com.example.rag.dto.AgentRouteDecision;
import com.example.rag.enums.QuestionType;

public interface AgentRouterService {

    AgentRouteDecision route(String question, QuestionType questionType);
}
