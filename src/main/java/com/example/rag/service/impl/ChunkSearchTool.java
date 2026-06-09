package com.example.rag.service.impl;

import com.example.rag.dto.AgentToolInput;
import com.example.rag.dto.AgentToolResult;
import com.example.rag.dto.ChunkHit;
import com.example.rag.dto.HybridSearchResult;
import com.example.rag.dto.QueryRewriteResult;
import com.example.rag.dto.RerankResult;
import com.example.rag.dto.RetrievalScoreDetail;
import com.example.rag.enums.ToolType;
import com.example.rag.service.AgentTool;
import com.example.rag.service.QueryRewriteService;
import com.example.rag.service.RerankService;
import com.example.rag.service.RetrievalScoreService;
import com.example.rag.service.SearchService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class ChunkSearchTool implements AgentTool {

    private static final int CANDIDATE_LIMIT = 50;
    private static final int QUERY_TOP_N = 10;
    private static final int MIN_DIVERSE_CANDIDATES = 3;

    private final SearchService searchService;
    private final QueryRewriteService queryRewriteService;
    private final RetrievalScoreService retrievalScoreService;
    private final RerankService rerankService;

    public ChunkSearchTool(SearchService searchService,
                           QueryRewriteService queryRewriteService,
                           RetrievalScoreService retrievalScoreService,
                           RerankService rerankService) {
        this.searchService = searchService;
        this.queryRewriteService = queryRewriteService;
        this.retrievalScoreService = retrievalScoreService;
        this.rerankService = rerankService;
    }

    @Override
    public ToolType type() {
        return ToolType.CHUNK_SEARCH;
    }

    @Override
    public AgentToolResult execute(AgentToolInput input) {
        long start = System.currentTimeMillis();
        AgentToolResult result = new AgentToolResult();
        result.setToolType(type());
        try {
            String question = input == null ? null : input.getQuestion();
            QueryRewriteResult rewrite = shouldUseOriginalBaseline(input)
                    ? null
                    : queryRewriteService.rewrite(question);
            List<ChunkHit> chunkHits = rewrite == null
                    ? searchSingleQuery(question)
                    : recallBySearchQueries(rewrite, input);
            if (rewrite != null) {
                List<RetrievalScoreDetail> scoreDetails = retrievalScoreService.score(
                        rewrite,
                        chunkHits,
                        input == null ? new ArrayList<>() : input.getRelationHits()
                );
                RerankResult rerankResult = rerankService.rerank(
                        rewrite,
                        chunkHits,
                        input == null ? new ArrayList<>() : input.getRelationHits(),
                        scoreDetails
                );
                chunkHits = rerankResult.getRerankedChunks();
                if (input != null) {
                    input.setRewriteResult(rewrite);
                    input.setRerankResult(rerankResult);
                }
            }
            result.setSuccess(true);
            result.setData(chunkHits);
            result.setSummary("chunkHits=" + chunkHits.size());
            result.setLatencyMs(System.currentTimeMillis() - start);
            return result;
        } catch (RuntimeException ex) {
            result.setSuccess(false);
            result.setErrorMessage(ex.getMessage());
            result.setSummary("chunk search failed");
            result.setLatencyMs(System.currentTimeMillis() - start);
            return result;
        }
    }

    private List<ChunkHit> searchSingleQuery(String question) {
        HybridSearchResult searchResult = searchService.searchHybrid(question);
        return searchResult == null || searchResult.getMergedChunks() == null
                ? new ArrayList<>()
                : searchResult.getMergedChunks();
    }

    private List<ChunkHit> recallBySearchQueries(QueryRewriteResult rewrite, AgentToolInput input) {
        LinkedHashMap<String, ChunkHit> merged = new LinkedHashMap<>();
        LinkedHashSet<String> matchedQueries = new LinkedHashSet<>();
        Map<String, Integer> countByQuery = new LinkedHashMap<>();
        int rawCandidateCount = 0;

        for (String query : safeQueries(rewrite)) {
            if (!StringUtils.hasText(query)) {
                continue;
            }
            List<ChunkHit> hits = limitHits(searchSingleQuery(query));
            countByQuery.put(query, hits.size());
            rawCandidateCount += hits.size();
            if (!hits.isEmpty()) {
                matchedQueries.add(query);
            }
            for (ChunkHit hit : hits) {
                if (merged.size() >= CANDIDATE_LIMIT) {
                    break;
                }
                String key = buildChunkKey(hit);
                if (!merged.containsKey(key)) {
                    hit.setMatchedQuery(query);
                    merged.put(key, hit);
                }
            }
            if (merged.size() >= CANDIDATE_LIMIT) {
                break;
            }
        }

        boolean fallbackUsed = false;
        if (merged.size() < MIN_DIVERSE_CANDIDATES) {
            int beforeFallbackRawCount = fallbackRawCount(countByQuery);
            fallbackUsed = recallFallbacks(rewrite, merged, matchedQueries, countByQuery) || fallbackUsed;
            rawCandidateCount += Math.max(0, fallbackRawCount(countByQuery) - beforeFallbackRawCount);
        }

        if (input != null) {
            input.setMatchedQueries(new ArrayList<>(matchedQueries));
            input.setRecallFallbackUsed(fallbackUsed);
            input.setCandidateCountByQuery(countByQuery);
            input.setCandidateCountAfterMerge(merged.size());
            input.setDuplicateCandidateRemovedCount(Math.max(0, rawCandidateCount - merged.size()));
        }
        return new ArrayList<>(merged.values());
    }

    private boolean recallFallbacks(QueryRewriteResult rewrite,
                                    LinkedHashMap<String, ChunkHit> merged,
                                    LinkedHashSet<String> matchedQueries,
                                    Map<String, Integer> countByQuery) {
        boolean fallbackUsed = false;
        fallbackUsed = recallFallbackQueries("fallback:entity:", rewrite.getDetectedEntities(), merged, matchedQueries, countByQuery) || fallbackUsed;
        if (merged.size() >= MIN_DIVERSE_CANDIDATES) {
            return fallbackUsed;
        }
        fallbackUsed = recallFallbackQueries("fallback:expandedKeyword:", rewrite.getExpandedKeywords(), merged, matchedQueries, countByQuery) || fallbackUsed;
        if (merged.size() >= MIN_DIVERSE_CANDIDATES) {
            return fallbackUsed;
        }
        fallbackUsed = recallFallbackQueries("fallback:intent:", intentFallbackKeywords(rewrite), merged, matchedQueries, countByQuery) || fallbackUsed;
        if (merged.size() >= MIN_DIVERSE_CANDIDATES) {
            return fallbackUsed;
        }
        if (StringUtils.hasText(rewrite.getOriginalQuestion())) {
            fallbackUsed = recallOne("fallback:originalQuestion", rewrite.getOriginalQuestion(), merged, matchedQueries, countByQuery) || fallbackUsed;
        }
        return fallbackUsed;
    }

    private boolean recallFallbackQueries(String prefix,
                                          List<String> queries,
                                          LinkedHashMap<String, ChunkHit> merged,
                                          LinkedHashSet<String> matchedQueries,
                                          Map<String, Integer> countByQuery) {
        boolean used = false;
        if (queries == null) {
            return false;
        }
        for (String query : queries) {
            if (!StringUtils.hasText(query)) {
                continue;
            }
            used = recallOne(prefix + query, query, merged, matchedQueries, countByQuery) || used;
            if (merged.size() >= MIN_DIVERSE_CANDIDATES) {
                break;
            }
        }
        return used;
    }

    private boolean recallOne(String label,
                              String query,
                              LinkedHashMap<String, ChunkHit> merged,
                              LinkedHashSet<String> matchedQueries,
                              Map<String, Integer> countByQuery) {
        List<ChunkHit> hits = limitHits(searchSingleQuery(query));
        countByQuery.put(label, hits.size());
        if (!hits.isEmpty()) {
            matchedQueries.add(label);
        }
        for (ChunkHit hit : hits) {
            if (merged.size() >= CANDIDATE_LIMIT) {
                break;
            }
            hit.setMatchedQuery(label);
            merged.putIfAbsent(buildChunkKey(hit), hit);
        }
        return true;
    }

    private int fallbackRawCount(Map<String, Integer> countByQuery) {
        int total = 0;
        for (Map.Entry<String, Integer> entry : countByQuery.entrySet()) {
            if (entry.getKey() != null && entry.getKey().startsWith("fallback:")) {
                total += entry.getValue() == null ? 0 : entry.getValue();
            }
        }
        return total;
    }

    private List<ChunkHit> limitHits(List<ChunkHit> hits) {
        if (hits == null || hits.size() <= QUERY_TOP_N) {
            return hits == null ? new ArrayList<>() : hits;
        }
        return new ArrayList<>(hits.subList(0, QUERY_TOP_N));
    }

    private List<String> intentFallbackKeywords(QueryRewriteResult rewrite) {
        List<String> keywords = new ArrayList<>();
        String intent = rewrite == null ? null : rewrite.getDetectedIntent();
        if (!StringUtils.hasText(intent)) {
            return keywords;
        }
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

    private String buildChunkKey(ChunkHit hit) {
        if (hit == null) {
            return "";
        }
        if (hit.getChunkId() != null) {
            return "id:" + hit.getChunkId();
        }
        return "doc:" + hit.getDocumentId() + ":idx:" + hit.getChunkIndex();
    }

    private boolean shouldUseOriginalBaseline(AgentToolInput input) {
        return input != null
                && input.getRouteDecision() != null
                && input.getRouteDecision().getReason() != null
                && input.getRouteDecision().getReason().toLowerCase().contains("forced baseline");
    }
}
