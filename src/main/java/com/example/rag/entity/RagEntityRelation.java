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
@Table(name = "RAG_ENTITY_RELATIONS")
public class RagEntityRelation {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "rag_entity_relations_seq_gen")
    @SequenceGenerator(name = "rag_entity_relations_seq_gen", sequenceName = "RAG_ENTITY_RELATIONS_SEQ", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "SOURCE_ENTITY_ID")
    private Long sourceEntityId;

    @Column(name = "TARGET_ENTITY_ID")
    private Long targetEntityId;

    @Column(name = "RELATION_TYPE")
    private String relationType;

    @Column(name = "DOCUMENT_ID")
    private Long documentId;

    @Column(name = "CHUNK_ID")
    private Long chunkId;

    @Column(name = "EVIDENCE_TEXT")
    private String evidenceText;

    @Column(name = "CONFIDENCE")
    private Double confidence;

    @Column(name = "CREATED_AT")
    private Date createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSourceEntityId() {
        return sourceEntityId;
    }

    public void setSourceEntityId(Long sourceEntityId) {
        this.sourceEntityId = sourceEntityId;
    }

    public Long getTargetEntityId() {
        return targetEntityId;
    }

    public void setTargetEntityId(Long targetEntityId) {
        this.targetEntityId = targetEntityId;
    }

    public String getRelationType() {
        return relationType;
    }

    public void setRelationType(String relationType) {
        this.relationType = relationType;
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

    public String getEvidenceText() {
        return evidenceText;
    }

    public void setEvidenceText(String evidenceText) {
        this.evidenceText = evidenceText;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
}
