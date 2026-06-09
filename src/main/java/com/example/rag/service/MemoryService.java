package com.example.rag.service;

import com.example.rag.dto.AgentExecutionTrace;
import com.example.rag.dto.AgentMemoryItem;
import com.example.rag.dto.AskResponse;
import com.example.rag.dto.EvalRunResult;
import com.example.rag.dto.QueryUnderstanding;

import java.util.List;

public interface MemoryService {

    void saveFromTrace(AgentExecutionTrace trace);

    void saveFromTrace(AgentExecutionTrace trace, AskResponse response, QueryUnderstanding understanding);

    void saveFromEvalResult(EvalRunResult evalResult);

    List<AgentMemoryItem> findRelevantMemories(String question, String intent, List<String> detectedEntities, String failureType);

    List<AgentMemoryItem> listRecentMemories(int limit);
}
