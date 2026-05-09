package com.example.rag.dto;

import java.util.ArrayList;
import java.util.List;

public class RelationBuildResult {

    private Long documentId;
    private Long chunkId;
    private Integer chunkCount = 0;
    private Integer entityCount = 0;
    private Integer relationCount = 0;
    private Integer createdTableHasFieldCount = 0;
    private Integer createdApiUsesTableCount = 0;
    private Integer createdMentionedWithCount = 0;
    private List<RelationHit> relations = new ArrayList<>();

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

    public Integer getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(Integer chunkCount) {
        this.chunkCount = chunkCount;
    }

    public Integer getEntityCount() {
        return entityCount;
    }

    public void setEntityCount(Integer entityCount) {
        this.entityCount = entityCount;
    }

    public Integer getRelationCount() {
        return relationCount;
    }

    public void setRelationCount(Integer relationCount) {
        this.relationCount = relationCount;
    }

    public Integer getCreatedTableHasFieldCount() {
        return createdTableHasFieldCount;
    }

    public void setCreatedTableHasFieldCount(Integer createdTableHasFieldCount) {
        this.createdTableHasFieldCount = createdTableHasFieldCount;
    }

    public Integer getCreatedApiUsesTableCount() {
        return createdApiUsesTableCount;
    }

    public void setCreatedApiUsesTableCount(Integer createdApiUsesTableCount) {
        this.createdApiUsesTableCount = createdApiUsesTableCount;
    }

    public Integer getCreatedMentionedWithCount() {
        return createdMentionedWithCount;
    }

    public void setCreatedMentionedWithCount(Integer createdMentionedWithCount) {
        this.createdMentionedWithCount = createdMentionedWithCount;
    }

    public List<RelationHit> getRelations() {
        return relations;
    }

    public void setRelations(List<RelationHit> relations) {
        this.relations = relations;
    }
}
