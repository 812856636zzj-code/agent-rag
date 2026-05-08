package com.example.rag.service;

import com.example.rag.dto.SearchResult;
import com.example.rag.dto.ChunkResponse;

import java.util.List;

public interface SearchService {

    SearchResult searchByKeyword(String keyword);

    List<ChunkResponse> searchChunkResponses(String keyword, int limit);
}
