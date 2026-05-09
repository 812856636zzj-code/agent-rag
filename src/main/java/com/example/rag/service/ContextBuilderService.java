package com.example.rag.service;

import com.example.rag.dto.AskContext;
import com.example.rag.dto.ChunkHit;
import com.example.rag.dto.HybridSearchResult;
import com.example.rag.dto.QueryUnderstanding;
import com.example.rag.dto.RelationQueryHit;
import com.example.rag.dto.SearchResult;

import java.util.List;

public interface ContextBuilderService {

    AskContext buildContext(String question, String keyword, List<ChunkHit> matchedChunks);

    AskContext buildContext(String question, String keyword, SearchResult searchResult);

    AskContext buildGraphContext(QueryUnderstanding understanding,
                                 HybridSearchResult hybridSearchResult,
                                 List<RelationQueryHit> relationHits);
}
