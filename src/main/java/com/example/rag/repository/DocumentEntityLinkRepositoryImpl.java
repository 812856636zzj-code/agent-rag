package com.example.rag.repository;

import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Repository
public class DocumentEntityLinkRepositoryImpl implements DocumentEntityLinkRepositoryCustom {

    private static final int DEFAULT_LIMIT = 100;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<Object[]> searchChunksByEntityNormalizedNames(List<String> normalizedNames) {
        if (CollectionUtils.isEmpty(normalizedNames)) {
            return new ArrayList<>();
        }

        Set<String> normalizedSet = new LinkedHashSet<>();
        for (String normalizedName : normalizedNames) {
            if (StringUtils.hasText(normalizedName)) {
                normalizedSet.add(normalizedName.trim().toLowerCase(Locale.ROOT));
            }
        }
        if (normalizedSet.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> names = new ArrayList<>(normalizedSet);
        StringBuilder sql = new StringBuilder();
        sql.append("select * from (");
        sql.append(" select ");
        sql.append(" c.ID as CHUNK_ID,");
        sql.append(" c.DOCUMENT_ID,");
        sql.append(" c.CHUNK_INDEX,");
        sql.append(" DBMS_LOB.SUBSTR(c.CONTENT, 4000, 1) as CONTENT,");
        sql.append(" e.ENTITY_NAME,");
        sql.append(" e.ENTITY_TYPE,");
        sql.append(" e.NORMALIZED_NAME");
        sql.append(" from RAG_ENTITIES e");
        sql.append(" join RAG_DOCUMENT_ENTITY_LINKS l on l.ENTITY_ID = e.ID");
        sql.append(" join RAG_DOCUMENT_CHUNKS c on c.ID = l.CHUNK_ID");
        sql.append(" join RAG_DOCUMENTS d on d.ID = c.DOCUMENT_ID");
        sql.append(" where l.CHUNK_ID is not null");
        sql.append(" and d.DOCUMENT_TYPE = 'KNOWLEDGE'");
        sql.append(" and (");

        for (int i = 0; i < names.size(); i++) {
            if (i > 0) {
                sql.append(" or ");
            }
            sql.append(" e.NORMALIZED_NAME = :exact").append(i);
            sql.append(" or e.NORMALIZED_NAME like :like").append(i);
        }

        sql.append(" )");
        sql.append(" order by c.DOCUMENT_ID desc, c.CHUNK_INDEX asc");
        sql.append(") where rownum <= :limit");

        Query query = entityManager.createNativeQuery(sql.toString());
        for (int i = 0; i < names.size(); i++) {
            query.setParameter("exact" + i, names.get(i));
            query.setParameter("like" + i, "%" + names.get(i) + "%");
        }
        query.setParameter("limit", DEFAULT_LIMIT);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        return rows;
    }
}
