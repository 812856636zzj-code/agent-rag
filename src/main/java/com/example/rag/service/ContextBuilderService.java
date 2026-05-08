package com.example.rag.service;

import com.example.rag.dto.AskContext;
import com.example.rag.dto.SearchResult;

public interface ContextBuilderService {

    AskContext buildContext(String question, String keyword, SearchResult searchResult);
}
