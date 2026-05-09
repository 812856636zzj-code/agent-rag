package com.example.rag.service.impl;

import com.example.rag.dto.QueryEntity;
import com.example.rag.dto.RelationQueryHit;
import com.example.rag.repository.RagEntityRelationRepository;
import com.example.rag.service.RelationQueryService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RelationQueryServiceImpl implements RelationQueryService {

    private final RagEntityRelationRepository ragEntityRelationRepository;

    public RelationQueryServiceImpl(RagEntityRelationRepository ragEntityRelationRepository) {
        this.ragEntityRelationRepository = ragEntityRelationRepository;
    }

    @Override
    public List<RelationQueryHit> queryRelations(String entityName) {
        return queryByEntityName(entityName);
    }

    @Override
    public List<RelationQueryHit> findRelations(List<QueryEntity> queryEntities) {
        Map<String, RelationQueryHit> dedup = new LinkedHashMap<>();
        if (queryEntities == null || queryEntities.isEmpty()) {
            return new ArrayList<>();
        }

        for (QueryEntity queryEntity : queryEntities) {
            if (queryEntity == null) {
                continue;
            }
            List<RelationQueryHit> hits = queryByEntityName(firstNonEmpty(queryEntity.getText(), queryEntity.getNormalizedText()));
            for (RelationQueryHit hit : hits) {
                dedup.putIfAbsent(buildKey(hit), hit);
            }
        }
        return new ArrayList<>(dedup.values());
    }

    private List<RelationQueryHit> queryByEntityName(String entityName) {
        if (!StringUtils.hasText(entityName)) {
            return new ArrayList<>();
        }

        List<Object[]> rows = ragEntityRelationRepository.findRelationsByEntityName(entityName);
        List<RelationQueryHit> results = new ArrayList<>();
        for (Object[] row : rows) {
            if (row == null || row.length < 5) {
                continue;
            }
            RelationQueryHit hit = new RelationQueryHit();
            hit.setEntityName(row[0] == null ? null : String.valueOf(row[0]));
            hit.setRelationType(row[1] == null ? null : String.valueOf(row[1]));
            hit.setDocumentId(row[2] == null ? null : ((Number) row[2]).longValue());
            hit.setChunkId(row[3] == null ? null : ((Number) row[3]).longValue());
            hit.setEvidenceText(row[4] == null ? null : String.valueOf(row[4]));
            results.add(hit);
        }
        return results;
    }

    private String buildKey(RelationQueryHit hit) {
        return String.valueOf(hit.getEntityName()) + "|" +
                String.valueOf(hit.getRelationType()) + "|" +
                String.valueOf(hit.getDocumentId()) + "|" +
                String.valueOf(hit.getChunkId());
    }

    private String firstNonEmpty(String left, String right) {
        if (StringUtils.hasText(left)) {
            return left;
        }
        return right;
    }
}
