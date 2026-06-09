package com.example.rag.service.impl;

import com.example.rag.dto.ChunkHit;
import com.example.rag.dto.QueryRewriteResult;
import com.example.rag.dto.RelationHit;
import com.example.rag.dto.RerankResult;
import com.example.rag.dto.RetrievalScoreDetail;
import com.example.rag.service.RerankService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class RerankServiceImpl implements RerankService {

    private static final int TOP_K = 5;

    @Override
    public RerankResult rerank(QueryRewriteResult rewrite,
                               List<ChunkHit> chunks,
                               List<RelationHit> relationHits,
                               List<RetrievalScoreDetail> scoreDetails) {
        List<ChunkHit> safeChunks = chunks == null ? new ArrayList<>() : new ArrayList<>(chunks);
        Map<Long, RetrievalScoreDetail> detailByChunkId = indexByChunkId(scoreDetails);
        List<ChunkHit> ranked = new ArrayList<>(safeChunks);
        ranked.sort(Comparator
                .comparingDouble((ChunkHit chunk) -> finalScore(chunk, detailByChunkId)).reversed()
                .thenComparing((ChunkHit chunk) -> entityCoverage(chunk, detailByChunkId), Comparator.reverseOrder())
                .thenComparing((ChunkHit chunk) -> intentMatch(chunk, detailByChunkId), Comparator.reverseOrder())
                .thenComparing((ChunkHit chunk) -> noisePenalty(chunk, detailByChunkId))
                .thenComparing(chunk -> chunk.getChunkIndex() == null ? Integer.MAX_VALUE : chunk.getChunkIndex()));

        if (ranked.size() > TOP_K) {
            ranked = new ArrayList<>(ranked.subList(0, TOP_K));
        }

        RerankResult result = new RerankResult();
        result.setRewrite(rewrite);
        result.setBeforeChunkIds(extractChunkIds(safeChunks));
        result.setAfterChunkIds(extractChunkIds(ranked));
        result.setRerankedChunks(ranked);
        result.setTopScoreDetails(extractTopDetails(ranked, detailByChunkId));
        fillQuality(result, safeChunks, ranked, detailByChunkId);
        return result;
    }

    private void fillQuality(RerankResult result,
                             List<ChunkHit> before,
                             List<ChunkHit> after,
                             Map<Long, RetrievalScoreDetail> detailByChunkId) {
        List<Double> scores = extractScores(before, detailByChunkId);
        result.setUniqueCandidateCount(countUniqueCandidates(before));
        result.setCandidateScoreSpread(round(scoreSpread(scores)));
        result.setTop1Score(scores.isEmpty() ? 0.0d : round(scores.get(0)));
        result.setTop2Score(scores.size() < 2 ? 0.0d : round(scores.get(1)));
        result.setTopScoreGap(round(result.getTop1Score() - result.getTop2Score()));
        result.setRerankChanged(isRerankChanged(before, after));
        result.setRerankChangeReason(resolveRerankChangeReason(result, before, scores));
    }

    private List<Double> extractScores(List<ChunkHit> chunks, Map<Long, RetrievalScoreDetail> detailByChunkId) {
        List<Double> scores = new ArrayList<>();
        if (chunks == null) {
            return scores;
        }
        for (ChunkHit chunk : chunks) {
            RetrievalScoreDetail detail = chunk == null ? null : detailByChunkId.get(chunk.getChunkId());
            scores.add(detail == null ? 0.0d : detail.getFinalScore());
        }
        scores.sort(Comparator.reverseOrder());
        return scores;
    }

    private double scoreSpread(List<Double> scores) {
        if (scores == null || scores.isEmpty()) {
            return 0.0d;
        }
        return scores.get(0) - scores.get(scores.size() - 1);
    }

    private int countUniqueCandidates(List<ChunkHit> chunks) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (chunks == null) {
            return 0;
        }
        for (ChunkHit chunk : chunks) {
            if (chunk != null && chunk.getChunkId() != null) {
                ids.add(chunk.getChunkId());
            }
        }
        return ids.size();
    }

    private boolean isRerankChanged(List<ChunkHit> before, List<ChunkHit> after) {
        List<Long> beforeIds = extractChunkIds(before);
        List<Long> afterIds = extractChunkIds(after);
        if (beforeIds.isEmpty() || afterIds.isEmpty()) {
            return false;
        }
        int compareSize = Math.min(beforeIds.size(), afterIds.size());
        for (int i = 0; i < compareSize; i++) {
            if (!beforeIds.get(i).equals(afterIds.get(i))) {
                return true;
            }
        }
        return false;
    }

    private String resolveRerankChangeReason(RerankResult result, List<ChunkHit> before, List<Double> scores) {
        if (result.getUniqueCandidateCount() <= 1) {
            return "SINGLE_CANDIDATE";
        }
        if (scores == null || scores.isEmpty() || result.getTop1Score() <= 0.0d) {
            return "WEAK_SCORING";
        }
        if (result.getCandidateScoreSpread() < 0.05d || result.getTopScoreGap() < 0.03d) {
            return "FLAT_SCORES";
        }
        if (result.isRerankChanged()) {
            return "TOP1_CHANGED";
        }
        if (result.getTopScoreGap() >= 0.05d) {
            return "CONFIDENT_STABLE";
        }
        return before == null || before.size() <= 1 ? "SINGLE_CANDIDATE" : "ORDER_UNCHANGED";
    }

    private double finalScore(ChunkHit chunk, Map<Long, RetrievalScoreDetail> detailByChunkId) {
        RetrievalScoreDetail detail = chunk == null ? null : detailByChunkId.get(chunk.getChunkId());
        return detail == null ? 0.0d : detail.getFinalScore();
    }

    private double entityCoverage(ChunkHit chunk, Map<Long, RetrievalScoreDetail> detailByChunkId) {
        RetrievalScoreDetail detail = chunk == null ? null : detailByChunkId.get(chunk.getChunkId());
        return detail == null ? 0.0d : detail.getEntityCoverage();
    }

    private double intentMatch(ChunkHit chunk, Map<Long, RetrievalScoreDetail> detailByChunkId) {
        RetrievalScoreDetail detail = chunk == null ? null : detailByChunkId.get(chunk.getChunkId());
        return detail == null ? 0.0d : detail.getIntentMatch();
    }

    private double noisePenalty(ChunkHit chunk, Map<Long, RetrievalScoreDetail> detailByChunkId) {
        RetrievalScoreDetail detail = chunk == null ? null : detailByChunkId.get(chunk.getChunkId());
        return detail == null ? 0.0d : detail.getNoisePenalty();
    }

    private Map<Long, RetrievalScoreDetail> indexByChunkId(List<RetrievalScoreDetail> scoreDetails) {
        Map<Long, RetrievalScoreDetail> detailByChunkId = new HashMap<>();
        if (scoreDetails == null) {
            return detailByChunkId;
        }
        for (RetrievalScoreDetail detail : scoreDetails) {
            if (detail != null && detail.getChunkId() != null) {
                detailByChunkId.put(detail.getChunkId(), detail);
            }
        }
        return detailByChunkId;
    }

    private Set<Long> buildRelationEvidenceChunkIds(List<RelationHit> relationHits) {
        Set<Long> chunkIds = new HashSet<>();
        if (relationHits == null) {
            return chunkIds;
        }
        for (RelationHit hit : relationHits) {
            if (hit != null && hit.getChunkId() != null && StringUtils.hasText(hit.getEvidenceText())) {
                chunkIds.add(hit.getChunkId());
            }
        }
        return chunkIds;
    }

    private List<Long> extractChunkIds(List<ChunkHit> chunks) {
        List<Long> chunkIds = new ArrayList<>();
        if (chunks == null) {
            return chunkIds;
        }
        for (ChunkHit chunk : chunks) {
            if (chunk != null && chunk.getChunkId() != null) {
                chunkIds.add(chunk.getChunkId());
            }
        }
        return chunkIds;
    }

    private List<RetrievalScoreDetail> extractTopDetails(List<ChunkHit> ranked,
                                                         Map<Long, RetrievalScoreDetail> detailByChunkId) {
        List<RetrievalScoreDetail> details = new ArrayList<>();
        for (ChunkHit chunk : ranked) {
            RetrievalScoreDetail detail = chunk == null ? null : detailByChunkId.get(chunk.getChunkId());
            if (detail != null) {
                details.add(detail);
            }
        }
        return details;
    }

    private double round(double value) {
        return Math.round(value * 10000.0d) / 10000.0d;
    }
}
