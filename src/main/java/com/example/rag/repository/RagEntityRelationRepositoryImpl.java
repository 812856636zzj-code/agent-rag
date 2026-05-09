package com.example.rag.repository;

import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
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
                                "case " +
                                " when lower(se.NORMALIZED_NAME) = :normalizedName or lower(se.ENTITY_NAME) = lower(:entityName) then te.ENTITY_NAME " +
                                " else se.ENTITY_NAME " +
                                "end as ADJ_ENTITY_NAME, " +
                                "r.RELATION_TYPE, " +
                                "r.DOCUMENT_ID, " +
                                "r.CHUNK_ID, " +
                                "r.EVIDENCE_TEXT " +
                                "from RAG_ENTITY_RELATIONS r " +
                                "join RAG_ENTITIES se on se.ID = r.SOURCE_ENTITY_ID " +
                                "join RAG_ENTITIES te on te.ID = r.TARGET_ENTITY_ID " +
                                "where " +
                                " (lower(se.NORMALIZED_NAME) = :normalizedName " +
                                "   or lower(te.NORMALIZED_NAME) = :normalizedName " +
                                "   or lower(se.ENTITY_NAME) = lower(:entityName) " +
                                "   or lower(te.ENTITY_NAME) = lower(:entityName)) " +
                                "order by r.CONFIDENCE desc nulls last, r.DOCUMENT_ID desc, r.CHUNK_ID asc")
                .setParameter("normalizedName", entityName.trim().toLowerCase(Locale.ROOT))
                .setParameter("entityName", entityName.trim())
                .getResultList();
        return rows;
    }

    @Override
    public List<Object[]> findRelationsByEntityIds(List<Long> entityIds) {
        if (CollectionUtils.isEmpty(entityIds)) {
            return new ArrayList<>();
        }

        StringBuilder sql = new StringBuilder();
        sql.append("select ");
        sql.append(" r.ID, ");
        sql.append(" r.SOURCE_ENTITY_ID, ");
        sql.append(" se.ENTITY_NAME as SOURCE_ENTITY_NAME, ");
        sql.append(" se.ENTITY_TYPE as SOURCE_ENTITY_TYPE, ");
        sql.append(" r.TARGET_ENTITY_ID, ");
        sql.append(" te.ENTITY_NAME as TARGET_ENTITY_NAME, ");
        sql.append(" te.ENTITY_TYPE as TARGET_ENTITY_TYPE, ");
        sql.append(" r.RELATION_TYPE, ");
        sql.append(" r.EVIDENCE_TEXT, ");
        sql.append(" r.DOCUMENT_ID, ");
        sql.append(" r.CHUNK_ID, ");
        sql.append(" r.CONFIDENCE, ");
        sql.append(" r.CREATED_AT ");
        sql.append("from RAG_ENTITY_RELATIONS r ");
        sql.append("join RAG_ENTITIES se on se.ID = r.SOURCE_ENTITY_ID ");
        sql.append("join RAG_ENTITIES te on te.ID = r.TARGET_ENTITY_ID ");
        sql.append("where (");
        for (int i = 0; i < entityIds.size(); i++) {
            if (i > 0) {
                sql.append(" or ");
            }
            sql.append("r.SOURCE_ENTITY_ID = :entityId").append(i);
            sql.append(" or r.TARGET_ENTITY_ID = :entityId").append(i);
        }
        sql.append(") order by r.CONFIDENCE desc nulls last, r.DOCUMENT_ID desc, r.CHUNK_ID asc");

        Query query = entityManager.createNativeQuery(sql.toString());
        for (int i = 0; i < entityIds.size(); i++) {
            query.setParameter("entityId" + i, entityIds.get(i));
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        return rows;
    }
}
