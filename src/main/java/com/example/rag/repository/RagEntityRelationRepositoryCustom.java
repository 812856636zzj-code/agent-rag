package com.example.rag.repository;

import java.util.List;

public interface RagEntityRelationRepositoryCustom {

    List<Object[]> findRelationsByEntityName(String entityName);

    List<Object[]> findRelationsByEntityIds(List<Long> entityIds);
}
