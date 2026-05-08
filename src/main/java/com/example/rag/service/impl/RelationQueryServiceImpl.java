package com.example.rag.service.impl;

import com.example.rag.dto.RelationQueryHit;
import com.example.rag.repository.RagEntityRelationRepository;
import com.example.rag.service.RelationQueryService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RelationQueryServiceImpl implements RelationQueryService {

    private final RagEntityRelationRepository ragEntityRelationRepository;

    public RelationQueryServiceImpl(RagEntityRelationRepository ragEntityRelationRepository) {
        this.ragEntityRelationRepository = ragEntityRelationRepository;
    }

    @Override
    public List<RelationQueryHit> queryRelations(String entityName) {
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
}
