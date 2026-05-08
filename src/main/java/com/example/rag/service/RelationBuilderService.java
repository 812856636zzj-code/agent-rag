package com.example.rag.service;

import com.example.rag.dto.RelationBuildResult;

public interface RelationBuilderService {

    RelationBuildResult buildRelationsForChunk(Long documentId, Long chunkId, String chunkText);

    RelationBuildResult rebuildRelationsForDocument(Long documentId);
}
