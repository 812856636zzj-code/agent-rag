package com.example.rag.service;

import com.example.rag.dto.QueryEntity;

import java.util.List;

public interface QueryEntityExtractionService {

    List<QueryEntity> extractQueryEntities(String question);
}
