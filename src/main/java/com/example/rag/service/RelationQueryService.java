package com.example.rag.service;

import com.example.rag.dto.QueryEntity;
import com.example.rag.dto.RelationQueryHit;

import java.util.List;

public interface RelationQueryService {

    List<RelationQueryHit> queryRelations(String entityName);

    List<RelationQueryHit> findRelations(List<QueryEntity> queryEntities);
}
