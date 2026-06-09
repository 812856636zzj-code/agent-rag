package com.example.rag.service;

import com.example.rag.dto.ChunkHit;
import com.example.rag.dto.QueryRewriteResult;
import com.example.rag.dto.RelationHit;
import com.example.rag.dto.RerankResult;
import com.example.rag.dto.RetrievalScoreDetail;

import java.util.List;

public interface RerankService {

    RerankResult rerank(QueryRewriteResult rewrite,
                        List<ChunkHit> chunks,
                        List<RelationHit> relationHits,
                        List<RetrievalScoreDetail> scoreDetails);
}
