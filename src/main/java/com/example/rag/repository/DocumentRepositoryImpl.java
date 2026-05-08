package com.example.rag.repository;

import com.example.rag.entity.Document;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;

@Repository
public class DocumentRepositoryImpl implements DocumentRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Optional<Document> findDuplicateByContent(String content) {
        if (!StringUtils.hasText(content)) {
            return Optional.empty();
        }

        @SuppressWarnings("unchecked")
        List<Document> documents = entityManager.createNativeQuery(
                        "select * from (" +
                                "select * from RAG_DOCUMENTS " +
                                "where DBMS_LOB.COMPARE(CONTENT, TO_CLOB(?1)) = 0" +
                                ") where rownum <= 1",
                        Document.class)
                .setParameter(1, content)
                .getResultList();

        if (documents.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(documents.get(0));
    }
}
