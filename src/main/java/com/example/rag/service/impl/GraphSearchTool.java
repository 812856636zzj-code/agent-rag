package com.example.rag.service.impl;

import com.example.rag.dto.AgentToolInput;
import com.example.rag.dto.AgentToolResult;
import com.example.rag.dto.ChunkHit;
import com.example.rag.dto.QueryRewriteResult;
import com.example.rag.dto.RelationHit;
import com.example.rag.dto.RerankResult;
import com.example.rag.dto.RetrievalScoreDetail;
import com.example.rag.enums.ToolType;
import com.example.rag.service.AgentTool;
import com.example.rag.service.QueryRewriteService;
import com.example.rag.service.RerankService;
import com.example.rag.service.RelationSearchService;
import com.example.rag.service.RetrievalScoreService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class GraphSearchTool implements AgentTool {

    private static final int RELATION_CANDIDATE_LIMIT = 20;
    private static final int MIN_RELATION_CANDIDATES = 3;

    private final RelationSearchService relationSearchService;
    private final QueryRewriteService queryRewriteService;
    private final RetrievalScoreService retrievalScoreService;
    private final RerankService rerankService;

    public GraphSearchTool(RelationSearchService relationSearchService,
                           QueryRewriteService queryRewriteService,
                           RetrievalScoreService retrievalScoreService,
                           RerankService rerankService) {
        this.relationSearchService = relationSearchService;
        this.queryRewriteService = queryRewriteService;
        this.retrievalScoreService = retrievalScoreService;
        this.rerankService = rerankService;
    }

    @Override
    public ToolType type() {
        return ToolType.GRAPH_SEARCH;
    }

    @Override
    public AgentToolResult execute(AgentToolInput input) {
        long start = System.currentTimeMillis();
        AgentToolResult result = new AgentToolResult();
        result.setToolType(type());
        try {
            QueryRewriteResult rewrite = queryRewriteService.rewrite(input == null ? null : input.getQuestion());
            if (input != null) {
                input.setRewriteResult(rewrite);
            }
            List<RelationHit> relationHits = searchRelationsWithFallback(rewrite, input);
            List<RelationHit> safeHits = relationHits == null ? new ArrayList<>() : relationHits;
            if (input != null && input.getRerankResult() == null) {
                List<ChunkHit> relationChunks = buildRelationEvidenceChunks(safeHits);
                List<RetrievalScoreDetail> scoreDetails = retrievalScoreService.score(rewrite, relationChunks, safeHits);
                RerankResult rerankResult = rerankService.rerank(rewrite, relationChunks, safeHits, scoreDetails);
                input.setRerankResult(rerankResult);
            }
            result.setSuccess(true);
            result.setData(safeHits);
            result.setSummary("relationHits=" + safeHits.size());
            result.setLatencyMs(System.currentTimeMillis() - start);
            return result;
        } catch (RuntimeException ex) {
            result.setSuccess(false);
            result.setErrorMessage(ex.getMessage());
            result.setSummary("relation search failed");
            result.setLatencyMs(System.currentTimeMillis() - start);
            return result;
        }
    }

    private List<RelationHit> searchRelationsWithFallback(QueryRewriteResult rewrite, AgentToolInput input) {
        LinkedHashSet<String> matchedQueries = new LinkedHashSet<>();
        Map<String, Integer> countByQuery = input == null ? new LinkedHashMap<>() : new LinkedHashMap<>(input.getCandidateCountByQuery());
        boolean fallbackUsed = input != null && input.isRecallFallbackUsed();
        LinkedHashMap<String, RelationHit> merged = new LinkedHashMap<>();

        for (String query : safeQueries(rewrite)) {
            List<RelationHit> hits = relationSearchService.searchRelations(query);
            countByQuery.put("relation:" + query, hits == null ? 0 : hits.size());
            if (hits != null && !hits.isEmpty()) {
                matchedQueries.add("relation:" + query);
                mergeRelationHits(merged, hits);
            }
            if (merged.size() >= MIN_RELATION_CANDIDATES) {
                applyRecallStats(input, matchedQueries, fallbackUsed, countByQuery, merged.size(), 0);
                return new ArrayList<>(merged.values());
            }
        }

        if (merged.size() < MIN_RELATION_CANDIDATES && rewrite.getExpandedKeywords() != null && !rewrite.getExpandedKeywords().isEmpty()) {
            fallbackUsed = true;
            String expandedQuery = String.join(" ", rewrite.getExpandedKeywords());
            List<RelationHit> hits = relationSearchService.searchRelations(expandedQuery);
            countByQuery.put("relation:fallback:expandedKeywords", hits == null ? 0 : hits.size());
            if (hits != null && !hits.isEmpty()) {
                matchedQueries.add("relation:fallback:expandedKeywords");
                mergeRelationHits(merged, hits);
            }
            if (merged.size() >= MIN_RELATION_CANDIDATES) {
                applyRecallStats(input, matchedQueries, fallbackUsed, countByQuery, hits.size(), 0);
                return new ArrayList<>(merged.values());
            }
        }

        List<RelationHit> entityHits = searchRelationFallbackQueries(
                "relation:fallback:entity:",
                rewrite.getDetectedEntities(),
                matchedQueries,
                countByQuery
        );
        if (entityHits != null && !entityHits.isEmpty()) {
            fallbackUsed = true;
            mergeRelationHits(merged, entityHits);
            if (merged.size() >= MIN_RELATION_CANDIDATES) {
                applyRecallStats(input, matchedQueries, fallbackUsed, countByQuery, merged.size(), 0);
                return new ArrayList<>(merged.values());
            }
        }

        List<RelationHit> keywordHits = searchRelationFallbackQueries(
                "relation:fallback:expandedKeyword:",
                rewrite.getExpandedKeywords(),
                matchedQueries,
                countByQuery
        );
        if (keywordHits != null && !keywordHits.isEmpty()) {
            fallbackUsed = true;
            mergeRelationHits(merged, keywordHits);
            if (merged.size() >= MIN_RELATION_CANDIDATES) {
                applyRecallStats(input, matchedQueries, fallbackUsed, countByQuery, merged.size(), 0);
                return new ArrayList<>(merged.values());
            }
        }

        List<RelationHit> intentHits = searchRelationFallbackQueries(
                "relation:fallback:intent:",
                intentFallbackKeywords(rewrite),
                matchedQueries,
                countByQuery
        );
        if (intentHits != null && !intentHits.isEmpty()) {
            fallbackUsed = true;
            mergeRelationHits(merged, intentHits);
            if (merged.size() >= MIN_RELATION_CANDIDATES) {
                applyRecallStats(input, matchedQueries, fallbackUsed, countByQuery, merged.size(), 0);
                return new ArrayList<>(merged.values());
            }
        }

        if (merged.size() < MIN_RELATION_CANDIDATES) {
            fallbackUsed = true;
            List<RelationHit> hits = relationSearchService.searchRelations(rewrite.getOriginalQuestion());
            countByQuery.put("relation:fallback:originalQuestion", hits == null ? 0 : hits.size());
            if (hits != null && !hits.isEmpty()) {
                matchedQueries.add("relation:fallback:originalQuestion");
                mergeRelationHits(merged, hits);
            }
        }
        applyRecallStats(input, matchedQueries, fallbackUsed, countByQuery, merged.size(), 0);
        return new ArrayList<>(merged.values());
    }

    private void mergeRelationHits(LinkedHashMap<String, RelationHit> merged, List<RelationHit> hits) {
        if (hits == null) {
            return;
        }
        for (RelationHit hit : hits) {
            if (merged.size() >= RELATION_CANDIDATE_LIMIT) {
                break;
            }
            merged.putIfAbsent(buildRelationKey(hit), hit);
        }
    }

    private String buildRelationKey(RelationHit hit) {
        if (hit == null) {
            return "";
        }
        return valueOrEmpty(hit.getSourceEntityName())
                + "|" + valueOrEmpty(hit.getRelationType())
                + "|" + valueOrEmpty(hit.getTargetEntityName())
                + "|" + (hit.getChunkId() == null ? "" : hit.getChunkId());
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private List<RelationHit> searchRelationFallbackQueries(String prefix,
                                                            List<String> queries,
                                                            Set<String> matchedQueries,
                                                            Map<String, Integer> countByQuery) {
        if (queries == null) {
            return new ArrayList<>();
        }
        for (String query : queries) {
            if (query == null || query.trim().isEmpty()) {
                continue;
            }
            String label = prefix + query;
            List<RelationHit> hits = relationSearchService.searchRelations(query);
            countByQuery.put(label, hits == null ? 0 : hits.size());
            if (hits != null && !hits.isEmpty()) {
                matchedQueries.add(label);
                return hits;
            }
        }
        return new ArrayList<>();
    }

    private List<String> intentFallbackKeywords(QueryRewriteResult rewrite) {
        List<String> keywords = new ArrayList<>();
        String intent = rewrite == null ? null : rewrite.getDetectedIntent();
        if ("RELATION".equals(intent)) {
            keywords.add("关系");
            keywords.add("依赖");
            keywords.add("DEPENDS_ON");
            keywords.add("API_BELONGS_TO_SERVICE");
        } else if ("TABLE_OR_FIELD".equals(intent)) {
            keywords.add("字段");
            keywords.add("COLUMN");
            keywords.add("TABLE");
            keywords.add("USER_TAB_COLUMNS");
        } else if ("SOURCE".equals(intent)) {
            keywords.add("source");
            keywords.add("来源");
            keywords.add("证据");
            keywords.add("evidence");
        } else if ("QUALITY_OR_NOISE".equals(intent)) {
            keywords.add("噪声");
            keywords.add("异常");
            keywords.add("问题");
            keywords.add("失败");
        }
        return keywords;
    }

    private List<String> safeQueries(QueryRewriteResult rewrite) {
        return rewrite == null || rewrite.getSearchQueries() == null ? new ArrayList<>() : rewrite.getSearchQueries();
    }

    private void applyRecallStats(AgentToolInput input,
                                  Set<String> matchedQueries,
                                  boolean fallbackUsed,
                                  Map<String, Integer> countByQuery,
                                  int candidateCount,
                                  int duplicateRemoved) {
        if (input == null) {
            return;
        }
        LinkedHashSet<String> mergedMatched = new LinkedHashSet<>(input.getMatchedQueries());
        mergedMatched.addAll(matchedQueries);
        input.setMatchedQueries(new ArrayList<>(mergedMatched));
        input.setRecallFallbackUsed(fallbackUsed);
        input.setCandidateCountByQuery(countByQuery);
        input.setCandidateCountAfterMerge(Math.max(input.getCandidateCountAfterMerge(), candidateCount));
        input.setDuplicateCandidateRemovedCount(input.getDuplicateCandidateRemovedCount() + duplicateRemoved);
    }

    private List<ChunkHit> buildRelationEvidenceChunks(List<RelationHit> relationHits) {
        List<ChunkHit> chunks = new ArrayList<>();
        if (relationHits == null) {
            return chunks;
        }
        Set<Long> seenChunkIds = new HashSet<>();
        for (RelationHit hit : relationHits) {
            if (hit == null || hit.getChunkId() == null) {
                continue;
            }
            if (!seenChunkIds.add(hit.getChunkId())) {
                continue;
            }
            ChunkHit chunk = new ChunkHit();
            chunk.setChunkId(hit.getChunkId());
            chunk.setDocumentId(hit.getDocumentId());
            chunk.setContent(hit.getEvidenceText());
            chunks.add(chunk);
        }
        return chunks;
    }
}
