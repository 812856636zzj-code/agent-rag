package com.example.rag.service;

import com.example.rag.dto.AskContext;
import com.example.rag.dto.ChunkHit;
import com.example.rag.dto.GraphAskResponse;
import com.example.rag.dto.HybridSearchResult;
import com.example.rag.dto.QueryUnderstanding;
import com.example.rag.dto.RelationQueryHit;
import com.example.rag.dto.StructuredContext;
import com.example.rag.entity.QueryLog;
import com.example.rag.repository.QueryLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class AskServiceImpl implements AskService {

    private static final Logger log = LoggerFactory.getLogger(AskServiceImpl.class);
    private static final int MAX_RELATION_HITS = 20;
    private static final Pattern ENTITY_LIKE_TERM_PATTERN = Pattern.compile("(/.*)|([A-Za-z][A-Za-z0-9_./-]*)|(RAG_[A-Z0-9_]+)");

    private final QueryUnderstandingService queryUnderstandingService;
    private final SearchService searchService;
    private final RelationQueryService relationQueryService;
    private final ContextBuilderService contextBuilderService;
    private final AnswerBuilderService answerBuilderService;
    private final QueryLogRepository queryLogRepository;

    public AskServiceImpl(QueryUnderstandingService queryUnderstandingService,
                          SearchService searchService,
                          RelationQueryService relationQueryService,
                          ContextBuilderService contextBuilderService,
                          AnswerBuilderService answerBuilderService,
                          QueryLogRepository queryLogRepository) {
        this.queryUnderstandingService = queryUnderstandingService;
        this.searchService = searchService;
        this.relationQueryService = relationQueryService;
        this.contextBuilderService = contextBuilderService;
        this.answerBuilderService = answerBuilderService;
        this.queryLogRepository = queryLogRepository;
    }

    @Override
    public GraphAskResponse ask(String question) {
        long start = System.currentTimeMillis();

        QueryUnderstanding understanding = queryUnderstandingService.understand(question);
        HybridSearchResult hybridSearchResult = searchService.searchHybrid(question);
        List<RelationQueryHit> relationHits = resolveRelationHits(understanding);
        AskContext askContext = contextBuilderService.buildGraphContext(understanding, hybridSearchResult, relationHits);
        StructuredContext structuredContext = askContext.getStructuredContextData();

        String finalKeyword = StringUtils.hasText(understanding.getKeyword())
                ? understanding.getKeyword()
                : hybridSearchResult.getKeyword();
        List<ChunkHit> chunks = buildResponseChunks(askContext.getTopChunks(), finalKeyword);
        String answer = answerBuilderService.buildGraphAnswer(askContext);

        GraphAskResponse response = new GraphAskResponse();
        response.setQuestion(question);
        response.setKeyword(finalKeyword);
        response.setChunks(chunks);
        response.setStructuredContext(structuredContext == null ? askContext.getStructuredContext() : structuredContext.getSummary());
        response.setAnswer(answer);
        response.setQueryEntities(understanding.getQueryEntities());
        response.setKeywordHitCount(hybridSearchResult.getKeywordHits() == null ? 0 : hybridSearchResult.getKeywordHits().size());
        response.setEntityHitCount(hybridSearchResult.getEntityHits() == null ? 0 : hybridSearchResult.getEntityHits().size());
        response.setMergedChunkCount(hybridSearchResult.getMergedChunks() == null ? 0 : hybridSearchResult.getMergedChunks().size());
        response.setRetrievalMode("GRAPH_HYBRID");
        response.setIntent(understanding.getIntent() == null ? null : understanding.getIntent().name());
        response.setFocusEntity(understanding.getFocusEntity());
        response.setMatchedEntities(structuredContext == null ? new ArrayList<>() : structuredContext.getMatchedEntities());
        response.setMatchedRelations(structuredContext == null ? new ArrayList<>() : structuredContext.getMatchedRelations());
        response.setRelationHitCount(relationHits.size());

        Long sourceDocId = chunks.isEmpty() ? null : chunks.get(0).getDocumentId();
        saveQueryLog(question, answer, sourceDocId, System.currentTimeMillis() - start);
        return response;
    }

    @Override
    public void saveQueryLog(String question, String answer, Long sourceDocId, long responseTimeMs) {
        QueryLog queryLog = new QueryLog();
        queryLog.setQuestion(question);
        queryLog.setAnswer(answer);
        queryLog.setSourceDocId(sourceDocId);
        queryLog.setResponseTimeMs(responseTimeMs);
        queryLog.setCreateTime(LocalDateTime.now());
        queryLogRepository.save(queryLog);
    }

    private List<RelationQueryHit> resolveRelationHits(QueryUnderstanding understanding) {
        List<RelationQueryHit> focusHits = new ArrayList<>();
        List<RelationQueryHit> fallbackHits = new ArrayList<>();

        try {
            if (understanding != null && StringUtils.hasText(understanding.getFocusEntity())) {
                focusHits = relationQueryService.queryRelations(understanding.getFocusEntity());
            }
        } catch (RuntimeException ex) {
            log.error("relation query by focusEntity failed, focusEntity = {}", understanding == null ? null : understanding.getFocusEntity(), ex);
        }

        if (focusHits.isEmpty() && understanding != null && understanding.getQueryTerms() != null) {
            for (String queryTerm : understanding.getQueryTerms()) {
                if (!looksLikeEntityTerm(queryTerm)) {
                    continue;
                }
                try {
                    List<RelationQueryHit> hits = relationQueryService.queryRelations(queryTerm);
                    fallbackHits.addAll(hits);
                } catch (RuntimeException ex) {
                    log.error("relation query by queryTerm failed, queryTerm = {}", queryTerm, ex);
                }
            }
        }

        Map<String, WeightedRelationHit> dedup = new LinkedHashMap<>();
        addRelationHits(dedup, focusHits, true, understanding == null ? null : understanding.getFocusEntity());
        addRelationHits(dedup, fallbackHits, false, understanding == null ? null : understanding.getFocusEntity());

        List<WeightedRelationHit> weightedHits = new ArrayList<>(dedup.values());
        weightedHits.sort(Comparator
                .comparingInt(WeightedRelationHit::getPriority).reversed()
                .thenComparing((WeightedRelationHit hit) -> hit.getDocumentId() == null ? Long.MIN_VALUE : hit.getDocumentId(), Comparator.reverseOrder())
                .thenComparing((WeightedRelationHit hit) -> hit.getRelationType() == null ? "" : hit.getRelationType()));

        List<RelationQueryHit> results = new ArrayList<>();
        for (WeightedRelationHit weightedHit : weightedHits) {
            if (results.size() >= MAX_RELATION_HITS) {
                break;
            }
            results.add(weightedHit.getHit());
        }
        return results;
    }

    private void addRelationHits(Map<String, WeightedRelationHit> dedup,
                                 List<RelationQueryHit> hits,
                                 boolean fromFocusEntity,
                                 String focusEntity) {
        for (RelationQueryHit hit : hits) {
            if (hit == null) {
                continue;
            }
            int priority = 0;
            if (fromFocusEntity) {
                priority += 20;
            }
            if (!"RELATED_TO".equalsIgnoreCase(hit.getRelationType())) {
                priority += 10;
            }
            if (StringUtils.hasText(focusEntity) && containsIgnoreCase(hit.getEvidenceText(), focusEntity)) {
                priority += 5;
            }
            if (StringUtils.hasText(focusEntity) && containsIgnoreCase(hit.getEntityName(), focusEntity)) {
                priority += 8;
            }
            String key = buildRelationKey(hit);
            WeightedRelationHit existing = dedup.get(key);
            if (existing == null || priority > existing.getPriority()) {
                dedup.put(key, new WeightedRelationHit(hit, priority));
            }
        }
    }

    private boolean looksLikeEntityTerm(String queryTerm) {
        return StringUtils.hasText(queryTerm) && ENTITY_LIKE_TERM_PATTERN.matcher(queryTerm.trim()).matches();
    }

    private boolean containsIgnoreCase(String text, String token) {
        return StringUtils.hasText(text) && StringUtils.hasText(token)
                && text.toLowerCase().contains(token.toLowerCase());
    }

    private String buildRelationKey(RelationQueryHit hit) {
        return String.valueOf(hit.getEntityName()) + "|" +
                String.valueOf(hit.getRelationType()) + "|" +
                String.valueOf(hit.getDocumentId()) + "|" +
                String.valueOf(hit.getChunkId());
    }

    private List<ChunkHit> buildResponseChunks(List<ChunkHit> hits, String keyword) {
        List<ChunkHit> chunks = new ArrayList<>();
        if (hits == null || hits.isEmpty()) {
            return chunks;
        }

        for (ChunkHit hit : hits) {
            ChunkHit chunk = new ChunkHit();
            chunk.setChunkId(hit.getChunkId());
            chunk.setDocumentId(hit.getDocumentId());
            chunk.setChunkIndex(hit.getChunkIndex());
            chunk.setTokenCount(hit.getTokenCount());
            chunk.setContent(buildSnippet(hit.getContent(), keyword));
            chunks.add(chunk);
        }
        return chunks;
    }

    private String buildSnippet(String content, String keyword) {
        if (!StringUtils.hasText(content)) {
            return content;
        }

        String trimmed = content.trim();
        if (!StringUtils.hasText(keyword)) {
            return buildSummary(trimmed, 120);
        }

        int index = trimmed.toLowerCase().indexOf(keyword.toLowerCase());
        if (index < 0) {
            return buildSummary(trimmed, 120);
        }

        int start = Math.max(0, index - 35);
        int end = Math.min(trimmed.length(), index + keyword.length() + 35);
        String prefix = start > 0 ? "..." : "";
        String suffix = end < trimmed.length() ? "..." : "";
        return prefix
                + trimmed.substring(start, index)
                + "[[" + trimmed.substring(index, Math.min(index + keyword.length(), trimmed.length())) + "]]"
                + trimmed.substring(Math.min(index + keyword.length(), trimmed.length()), end)
                + suffix;
    }

    private String buildSummary(String content, int maxLength) {
        if (!StringUtils.hasText(content)) {
            return content;
        }
        if (content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength) + "...";
    }

    private static class WeightedRelationHit {
        private final RelationQueryHit hit;
        private final int priority;

        private WeightedRelationHit(RelationQueryHit hit, int priority) {
            this.hit = hit;
            this.priority = priority;
        }

        public RelationQueryHit getHit() {
            return hit;
        }

        public int getPriority() {
            return priority;
        }

        public String getRelationType() {
            return hit == null ? null : hit.getRelationType();
        }

        public Long getDocumentId() {
            return hit == null ? null : hit.getDocumentId();
        }
    }
}
