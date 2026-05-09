package com.example.rag.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import java.util.Date;

@Entity
@Table(name = "RAG_DOCUMENT_ENTITY_LINKS")
public class DocumentEntityLink {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "rag_doc_entity_links_seq_gen")
    @SequenceGenerator(name = "rag_doc_entity_links_seq_gen", sequenceName = "RAG_DOCUMENT_ENTITY_LINKS_SEQ", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "DOCUMENT_ID")
    private Long documentId;

    @Column(name = "CHUNK_ID")
    private Long chunkId;

    @Column(name = "ENTITY_ID")
    private Long entityId;

    @Column(name = "SOURCE_TEXT")
    private String sourceText;

    @Column(name = "CREATED_AT")
    private Date createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public Long getChunkId() {
        return chunkId;
    }

    public void setChunkId(Long chunkId) {
        this.chunkId = chunkId;
    }

    public Long getEntityId() {
        return entityId;
    }

    public void setEntityId(Long entityId) {
        this.entityId = entityId;
    }

    public String getSourceText() {
        return sourceText;
    }

    public void setSourceText(String sourceText) {
        this.sourceText = sourceText;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
}
