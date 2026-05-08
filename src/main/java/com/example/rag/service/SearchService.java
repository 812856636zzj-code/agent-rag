package com.example.rag.service;

import com.example.rag.dto.SearchResult;

public interface SearchService {

    SearchResult searchByKeyword(String keyword);

    SearchResult searchByEntity(String keywordOrQuestion);

    SearchResult searchHybrid(String question);

    SearchResult searchByQuestion(String question);
}
