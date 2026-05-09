package com.example.rag.dto;

import java.util.ArrayList;
import java.util.List;

public class RelationBuildResult {

    private Long documentId;
    private Long chunkId;
    private Integer entityCount = 0;
    private Integer relationCount = 0;
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

    public List<RelationHit> getRelations() {
        return relations;
    }

    public void setRelations(List<RelationHit> relations) {
        this.relations = relations;
    }
}
