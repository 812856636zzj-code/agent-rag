package com.example.rag.service.impl;

import com.example.rag.repository.DocumentEntityLinkRepository;
import com.example.rag.service.EntityQueryService;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class EntityQueryServiceImpl implements EntityQueryService {

    private static final int DEFAULT_LIMIT = 50;

    private final DocumentEntityLinkRepository documentEntityLinkRepository;

    public EntityQueryServiceImpl(DocumentEntityLinkRepository documentEntityLinkRepository) {
        this.documentEntityLinkRepository = documentEntityLinkRepository;
    }

    @Override
    public List<Map<String, Object>> getRecentEntities(int limit) {
        int safeLimit = normalizeLimit(limit);
        return mapRows(documentEntityLinkRepository.findRecentEntityLinks(safeLimit), safeLimit);
    }

    @Override
    public List<Map<String, Object>> getEntitiesByDocumentId(Long documentId, int limit) {
        int safeLimit = normalizeLimit(limit);
        List<Object[]> rows = documentEntityLinkRepository.findEntityLinksByDocumentId(documentId);
        return mapRows(rows, safeLimit);
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, DEFAULT_LIMIT);
    }

    private List<Map<String, Object>> mapRows(List<Object[]> rows, int limit) {
        List<Map<String, Object>> results = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            return results;
        }

        int max = Math.min(rows.size(), limit);
        for (int i = 0; i < max; i++) {
            Object[] row = rows.get(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("entityName", row[0]);
            item.put("entityType", row[1]);
            item.put("normalizedName", row[2]);
            item.put("documentId", row[3]);
            item.put("chunkId", row[4]);
            item.put("sourceText", row[5]);
            item.put("createdAt", convertTimestamp(row[6]));
            results.add(item);
        }
        return results;
    }

    private Object convertTimestamp(Object value) {
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toInstant().toString();
        }
        return value;
    }
}
