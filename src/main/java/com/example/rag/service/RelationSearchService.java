package com.example.rag.service;

import com.example.rag.dto.RelationHit;
import com.example.rag.dto.RelationSearchResult;

import java.util.List;

public interface RelationSearchService {

    List<RelationHit> searchRelations(String question);

    RelationSearchResult searchRelationsWithDebug(String question);
}
