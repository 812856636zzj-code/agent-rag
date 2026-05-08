package com.example.rag.repository;

import java.util.List;

public interface RagEntityRelationRepositoryCustom {

    List<Object[]> findRelationsByEntityName(String entityName);
}
