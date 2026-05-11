package com.example.rag.service.impl;

import com.example.rag.dto.AgentToolInput;
import com.example.rag.dto.AgentToolResult;
import com.example.rag.dto.ChunkHit;
import com.example.rag.dto.HybridSearchResult;
import com.example.rag.enums.ToolType;
import com.example.rag.service.AgentTool;
import com.example.rag.service.SearchService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ChunkSearchTool implements AgentTool {

    private final SearchService searchService;

    public ChunkSearchTool(SearchService searchService) {
        this.searchService = searchService;
    }

    @Override
    public ToolType type() {
        return ToolType.CHUNK_SEARCH;
    }

    @Override
    public AgentToolResult execute(AgentToolInput input) {
        long start = System.currentTimeMillis();
        AgentToolResult result = new AgentToolResult();
        result.setToolType(type());
        try {
            HybridSearchResult searchResult = searchService.searchHybrid(input == null ? null : input.getQuestion());
            List<ChunkHit> chunkHits = searchResult == null || searchResult.getMergedChunks() == null
                    ? new ArrayList<>()
                    : searchResult.getMergedChunks();
            result.setSuccess(true);
            result.setData(chunkHits);
            result.setSummary("chunkHits=" + chunkHits.size());
            result.setLatencyMs(System.currentTimeMillis() - start);
            return result;
        } catch (RuntimeException ex) {
            result.setSuccess(false);
            result.setErrorMessage(ex.getMessage());
            result.setSummary("chunk search failed");
            result.setLatencyMs(System.currentTimeMillis() - start);
            return result;
        }
    }
}
