package com.example.rag.service;

import com.example.rag.dto.ChunkHit;
import com.example.rag.dto.QueryRewriteResult;
import com.example.rag.dto.RelationHit;
import com.example.rag.dto.RetrievalScoreDetail;

import java.util.List;

public interface RetrievalScoreService {

    List<RetrievalScoreDetail> score(QueryRewriteResult rewrite, List<ChunkHit> chunks, List<RelationHit> relationHits);
}
