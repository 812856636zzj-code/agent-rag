package com.example.rag.service;

import com.example.rag.dto.SearchResult;

public interface SearchService {

    SearchResult searchByKeyword(String keyword);

    SearchResult searchByQuestion(String question);
}
