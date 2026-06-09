package com.example.rag.service.impl;

import com.example.rag.dto.ChunkHit;
import com.example.rag.dto.QueryRewriteResult;
import com.example.rag.dto.RelationHit;
import com.example.rag.dto.RetrievalScoreDetail;
import com.example.rag.service.RetrievalScoreService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class RetrievalScoreServiceImpl implements RetrievalScoreService {

    private static final double KEYWORD_WEIGHT = 0.40d;
    private static final double ENTITY_WEIGHT = 0.25d;
    private static final double RELATION_WEIGHT = 0.25d;
    private static final double SOURCE_WEIGHT = 0.10d;
    private static final double INTENT_WEIGHT = 0.10d;

    @Override
    public List<RetrievalScoreDetail> score(QueryRewriteResult rewrite, List<ChunkHit> chunks, List<RelationHit> relationHits) {
        List<RetrievalScoreDetail> details = new ArrayList<>();
        if (chunks == null || chunks.isEmpty()) {
            return details;
        }
        for (ChunkHit chunk : chunks) {
            RetrievalScoreDetail detail = new RetrievalScoreDetail();
            detail.setChunkId(chunk.getChunkId());
            detail.setDocumentId(chunk.getDocumentId());
            detail.setChunkIndex(chunk.getChunkIndex());
            detail.setKeywordCoverage(keywordScore(rewrite, chunk));
            detail.setEntityCoverage(entityScore(rewrite, chunk));
            detail.setKeywordScore(detail.getKeywordCoverage());
            detail.setEntityScore(detail.getEntityCoverage());
            detail.setRelationScore(relationScore(rewrite, chunk, relationHits));
            detail.setSourceScore(sourceScore(chunk));
            detail.setIntentMatch(intentMatch(rewrite, chunk));
            detail.setSourceType(resolveSourceType(chunk, relationHits));
            detail.setNoisePenalty(noisePenalty(chunk));
            detail.setFinalScore(round(
                    detail.getKeywordScore() * KEYWORD_WEIGHT
                            + detail.getEntityScore() * ENTITY_WEIGHT
                            + detail.getRelationScore() * RELATION_WEIGHT
                            + detail.getSourceScore() * SOURCE_WEIGHT
                            + detail.getIntentMatch() * INTENT_WEIGHT
                            - detail.getNoisePenalty()
            ));
            detail.setNotes(buildNotes(detail));
            chunk.setScoreDetail(detail);
            details.add(detail);
        }
        return details;
    }

    private double keywordScore(QueryRewriteResult rewrite, ChunkHit chunk) {
        List<String> terms = rewrite == null ? new ArrayList<>() : rewrite.getExpandedKeywords();
        return matchRatio(chunk == null ? "" : chunk.getContent(), terms);
    }

    private double entityScore(QueryRewriteResult rewrite, ChunkHit chunk) {
        List<String> entities = rewrite == null ? new ArrayList<>() : rewrite.getDetectedEntities();
        return matchRatio(chunk == null ? "" : chunk.getContent(), entities);
    }

    private double relationScore(QueryRewriteResult rewrite, ChunkHit chunk, List<RelationHit> relationHits) {
        if (chunk == null || relationHits == null || relationHits.isEmpty()) {
            return 0.0d;
        }
        double best = 0.0d;
        Set<String> terms = new HashSet<>();
        if (rewrite != null && rewrite.getExpandedKeywords() != null) {
            terms.addAll(rewrite.getExpandedKeywords());
        }
        if (rewrite != null && rewrite.getDetectedEntities() != null) {
            terms.addAll(rewrite.getDetectedEntities());
        }
        for (RelationHit hit : relationHits) {
            if (hit == null) {
                continue;
            }
            double score = 0.0d;
            if (chunk.getChunkId() != null && chunk.getChunkId().equals(hit.getChunkId())) {
                score += 0.6d;
            }
            String relationText = join(hit.getSourceEntityName(), hit.getRelationType(), hit.getTargetEntityName(), hit.getEvidenceText());
            score += 0.4d * matchRatio(relationText, new ArrayList<>(terms));
            best = Math.max(best, Math.min(1.0d, score));
        }
        return round(best);
    }

    private double sourceScore(ChunkHit chunk) {
        if (chunk == null) {
            return 0.0d;
        }
        if (chunk.getDocumentId() == null && chunk.getChunkIndex() == null) {
            return 0.0d;
        }
        double score = 0.5d;
        if (chunk.getDocumentId() != null) {
            score += 0.25d;
        }
        if (chunk.getChunkIndex() != null) {
            score += Math.max(0.0d, 0.25d - Math.min(chunk.getChunkIndex(), 10) * 0.02d);
        }
        return round(Math.min(1.0d, score));
    }

    private double intentMatch(QueryRewriteResult rewrite, ChunkHit chunk) {
        if (rewrite == null || !StringUtils.hasText(rewrite.getDetectedIntent()) || chunk == null) {
            return 0.0d;
        }
        String content = chunk.getContent() == null ? "" : chunk.getContent().toLowerCase(Locale.ROOT);
        String intent = rewrite.getDetectedIntent();
        if ("RELATION".equals(intent)) {
            return containsAny(content, "depends_on", "api_belongs_to_service", "belongs_to", "关系", "依赖") ? 1.0d : 0.0d;
        }
        if ("TABLE_OR_FIELD".equals(intent)) {
            return containsAny(content, "user_tab_columns", "column", "table", "字段", "表") ? 1.0d : 0.0d;
        }
        if ("SOURCE".equals(intent)) {
            return containsAny(content, "source", "sources", "evidence", "来源", "证据") ? 1.0d : 0.0d;
        }
        if ("QUALITY_OR_NOISE".equals(intent)) {
            return containsAny(content, "test_spec", "oracle.sql.clob", "噪声", "异常", "问题") ? 1.0d : 0.0d;
        }
        if ("ENTITY".equals(intent)) {
            return entityScore(rewrite, chunk) > 0.0d ? 1.0d : 0.0d;
        }
        return 0.0d;
    }

    private String resolveSourceType(ChunkHit chunk, List<RelationHit> relationHits) {
        if (chunk != null && relationHits != null) {
            for (RelationHit hit : relationHits) {
                if (hit != null && chunk.getChunkId() != null && chunk.getChunkId().equals(hit.getChunkId())) {
                    return "RELATION_EVIDENCE";
                }
            }
        }
        if (chunk == null || chunk.getDocumentId() == null) {
            return "UNKNOWN";
        }
        return "DOCUMENT_CHUNK";
    }

    private double noisePenalty(ChunkHit chunk) {
        String content = chunk == null ? "" : chunk.getContent();
        if (!StringUtils.hasText(content)) {
            return 0.12d;
        }
        String lower = content.toLowerCase(Locale.ROOT);
        double penalty = 0.0d;
        if (lower.contains("test_spec") || lower.contains("oracle.sql.clob") || lower.contains("[object object]")) {
            penalty += 0.25d;
        }
        if (content.length() > 1200) {
            penalty += 0.05d;
        }
        return round(penalty);
    }

    private boolean containsAny(String text, String... terms) {
        if (!StringUtils.hasText(text) || terms == null) {
            return false;
        }
        for (String term : terms) {
            if (StringUtils.hasText(term) && text.contains(term.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private double matchRatio(String text, List<String> terms) {
        if (!StringUtils.hasText(text) || terms == null || terms.isEmpty()) {
            return 0.0d;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        int total = 0;
        int hit = 0;
        for (String term : terms) {
            if (!StringUtils.hasText(term)) {
                continue;
            }
            total++;
            if (lower.contains(term.toLowerCase(Locale.ROOT))) {
                hit++;
            }
        }
        return total == 0 ? 0.0d : round((double) hit / total);
    }

    private String buildNotes(RetrievalScoreDetail detail) {
        List<String> notes = new ArrayList<>();
        if (detail.getKeywordScore() > 0) {
            notes.add("keyword");
        }
        if (detail.getEntityScore() > 0) {
            notes.add("entity");
        }
        if (detail.getRelationScore() > 0) {
            notes.add("relation");
        }
        if (detail.getIntentMatch() > 0) {
            notes.add("intent");
        }
        if (detail.getNoisePenalty() > 0) {
            notes.add("noisePenalty");
        }
        return String.join("+", notes);
    }

    private String join(String... values) {
        List<String> parts = new ArrayList<>();
        if (values != null) {
            for (String value : values) {
                if (StringUtils.hasText(value)) {
                    parts.add(value);
                }
            }
        }
        return String.join(" ", parts);
    }

    private double round(double value) {
        return Math.round(value * 10000.0d) / 10000.0d;
    }
}
