package com.example.rag.repository;

import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Repository
public class RagEntityRelationRepositoryImpl implements RagEntityRelationRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<Object[]> findRelationsByEntityName(String entityName) {
        if (!StringUtils.hasText(entityName)) {
            return new ArrayList<>();
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(
                        "select " +
                                "case when lower(se.NORMALIZED_NAME) = :normalizedName then te.ENTITY_NAME else se.ENTITY_NAME end as ADJ_ENTITY_NAME, " +
                                "r.RELATION_TYPE, " +
                                "r.DOCUMENT_ID, " +
                                "r.CHUNK_ID, " +
                                "r.EVIDENCE_TEXT " +
                                "from RAG_ENTITY_RELATIONS r " +
                                "join RAG_ENTITIES se on se.ID = r.SOURCE_ENTITY_ID " +
                                "join RAG_ENTITIES te on te.ID = r.TARGET_ENTITY_ID " +
                                "where lower(se.NORMALIZED_NAME) = :normalizedName " +
                                "   or lower(te.NORMALIZED_NAME) = :normalizedName " +
                                "order by r.DOCUMENT_ID desc, r.CHUNK_ID asc")
                .setParameter("normalizedName", entityName.trim().toLowerCase(Locale.ROOT))
                .getResultList();
        return rows;
    }
}
