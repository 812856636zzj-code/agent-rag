package com.example.rag.service;

import java.util.List;
import java.util.Map;

public interface EntityQueryService {

    List<Map<String, Object>> getRecentEntities(int limit);

    List<Map<String, Object>> getEntitiesByDocumentId(Long documentId, int limit);
}
