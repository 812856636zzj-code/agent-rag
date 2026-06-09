package com.example.rag.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "RAG_AGENT_MEMORIES")
public class RagAgentMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "rag_agent_memories_seq_gen")
    @SequenceGenerator(name = "rag_agent_memories_seq_gen", sequenceName = "RAG_AGENT_MEMORIES_SEQ", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "MEMORY_TYPE")
    private String memoryType;

    @Column(name = "MEMORY_KEY")
    private String memoryKey;

    @Lob
    @Column(name = "MEMORY_VALUE")
    private String memoryValue;

    @Column(name = "INTENT")
    private String intent;

    @Column(name = "ENTITY_NAME")
    private String entityName;

    @Column(name = "FAILURE_TYPE")
    private String failureType;

    @Column(name = "SOURCE_TYPE")
    private String sourceType;

    @Column(name = "SOURCE_ID")
    private String sourceId;

    @Column(name = "CONFIDENCE")
    private Double confidence;

    @Column(name = "USAGE_COUNT")
    private Integer usageCount;

    @Column(name = "LAST_USED_AT")
    private LocalDateTime lastUsedAt;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMemoryType() {
        return memoryType;
    }

    public void setMemoryType(String memoryType) {
        this.memoryType = memoryType;
    }

    public String getMemoryKey() {
        return memoryKey;
    }

    public void setMemoryKey(String memoryKey) {
        this.memoryKey = memoryKey;
    }

    public String getMemoryValue() {
        return memoryValue;
    }

    public void setMemoryValue(String memoryValue) {
        this.memoryValue = memoryValue;
    }

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public String getEntityName() {
        return entityName;
    }

    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }

    public String getFailureType() {
        return failureType;
    }

    public void setFailureType(String failureType) {
        this.failureType = failureType;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public Integer getUsageCount() {
        return usageCount;
    }

    public void setUsageCount(Integer usageCount) {
        this.usageCount = usageCount;
    }

    public LocalDateTime getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(LocalDateTime lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
