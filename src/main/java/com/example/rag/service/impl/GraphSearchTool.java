package com.example.rag.service.impl;

import com.example.rag.dto.AgentToolInput;
import com.example.rag.dto.AgentToolResult;
import com.example.rag.dto.RelationHit;
import com.example.rag.enums.ToolType;
import com.example.rag.service.AgentTool;
import com.example.rag.service.RelationSearchService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class GraphSearchTool implements AgentTool {

    private final RelationSearchService relationSearchService;

    public GraphSearchTool(RelationSearchService relationSearchService) {
        this.relationSearchService = relationSearchService;
    }

    @Override
    public ToolType type() {
        return ToolType.GRAPH_SEARCH;
    }

    @Override
    public AgentToolResult execute(AgentToolInput input) {
        long start = System.currentTimeMillis();
        AgentToolResult result = new AgentToolResult();
        result.setToolType(type());
        try {
            List<RelationHit> relationHits = relationSearchService.searchRelations(input == null ? null : input.getQuestion());
            List<RelationHit> safeHits = relationHits == null ? new ArrayList<>() : relationHits;
            result.setSuccess(true);
            result.setData(safeHits);
            result.setSummary("relationHits=" + safeHits.size());
            result.setLatencyMs(System.currentTimeMillis() - start);
            return result;
        } catch (RuntimeException ex) {
            result.setSuccess(false);
            result.setErrorMessage(ex.getMessage());
            result.setSummary("relation search failed");
            result.setLatencyMs(System.currentTimeMillis() - start);
            return result;
        }
    }
}
