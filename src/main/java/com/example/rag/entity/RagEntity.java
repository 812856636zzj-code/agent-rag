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
@Table(name = "RAG_ENTITIES")
public class RagEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "rag_entities_seq_gen")
    @SequenceGenerator(name = "rag_entities_seq_gen", sequenceName = "RAG_ENTITIES_SEQ", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "ENTITY_NAME")
    private String entityName;

    @Column(name = "ENTITY_TYPE")
    private String entityType;

    @Column(name = "NORMALIZED_NAME")
    private String normalizedName;

    @Column(name = "CREATED_AT")
    private Date createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEntityName() {
        return entityName;
    }

    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public void setNormalizedName(String normalizedName) {
        this.normalizedName = normalizedName;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
}
