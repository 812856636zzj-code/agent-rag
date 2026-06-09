package com.example.rag.service;

import com.example.rag.dto.AskContext;
import com.example.rag.dto.AskDebugInfo;
import com.example.rag.dto.AskResponse;
import com.example.rag.dto.AgentRouteDecision;
import com.example.rag.dto.ChunkHit;
import com.example.rag.dto.HybridSearchResult;
import com.example.rag.dto.QueryUnderstanding;
import com.example.rag.dto.RelationHit;
import com.example.rag.dto.RelationPathItem;
import com.example.rag.dto.RelationQueryHit;
import com.example.rag.dto.RelationSearchResult;
import com.example.rag.dto.SourceItem;
import com.example.rag.dto.StructuredContext;
import com.example.rag.entity.Document;
import com.example.rag.entity.DocumentChunk;
import com.example.rag.entity.QueryLog;
import com.example.rag.enums.QuestionType;
import com.example.rag.enums.QueryIntent;
import com.example.rag.enums.ToolType;
import com.example.rag.repository.DocumentChunkRepository;
import com.example.rag.repository.DocumentRepository;
import com.example.rag.repository.QueryLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class AskServiceImpl implements AskService {

    private static final Logger log = LoggerFactory.getLogger(AskServiceImpl.class);
    private static final int MAX_RELATION_HITS = 20;
    private static final Pattern ENTITY_LIKE_TERM_PATTERN = Pattern.compile("(/.*)|([A-Za-z][A-Za-z0-9_./-]*)|(RAG_[A-Z0-9_]+)");

    private final QueryUnderstandingService queryUnderstandingService;
    private final QuestionClassifier questionClassifier;
    private final AgentRouterService agentRouterService;
    private final AgentExecutor agentExecutor;
    private final SearchService searchService;
    private final RelationQueryService relationQueryService;
    private final RelationSearchService relationSearchService;
    private final ContextBuilderService contextBuilderService;
    private final AnswerBuilderService answerBuilderService;
    private final EvidenceSelector evidenceSelector;
    private final DocumentChunkRepository documentChunkRepository;
    private final DocumentRepository documentRepository;
    private final QueryLogRepository queryLogRepository;

    public AskServiceImpl(QueryUnderstandingService queryUnderstandingService,
                          QuestionClassifier questionClassifier,
                          AgentRouterService agentRouterService,
                          AgentExecutor agentExecutor,
                          SearchService searchService,
                          RelationQueryService relationQueryService,
                          RelationSearchService relationSearchService,
                          ContextBuilderService contextBuilderService,
                          AnswerBuilderService answerBuilderService,
                          EvidenceSelector evidenceSelector,
                          DocumentChunkRepository documentChunkRepository,
                          DocumentRepository documentRepository,
                          QueryLogRepository queryLogRepository) {
        this.queryUnderstandingService = queryUnderstandingService;
        this.questionClassifier = questionClassifier;
        this.agentRouterService = agentRouterService;
        this.agentExecutor = agentExecutor;
        this.searchService = searchService;
        this.relationQueryService = relationQueryService;
        this.relationSearchService = relationSearchService;
        this.contextBuilderService = contextBuilderService;
        this.answerBuilderService = answerBuilderService;
        this.evidenceSelector = evidenceSelector;
        this.documentChunkRepository = documentChunkRepository;
        this.documentRepository = documentRepository;
        this.queryLogRepository = queryLogRepository;
    }

    @Override
    public AskResponse ask(String question) {
        return ask(question, "graph");
    }

    @Override
    public AskResponse ask(String question, String mode) {
        if ("baseline".equalsIgnoreCase(normalizeMode(mode))) {
            QuestionType questionType = questionClassifier.classify(question);
            AgentRouteDecision routeDecision = buildForcedBaselineRouteDecision(questionType);
            return agentExecutor.execute(question, routeDecision);
        }
        return askByQuestionType(question);
    }

    private AskResponse askByQuestionType(String question) {
        QuestionType questionType = questionClassifier.classify(question);
        AgentRouteDecision routeDecision = agentRouterService.route(question, questionType);
        return agentExecutor.execute(question, routeDecision);
    }

    private AskResponse askBaseline(String question) {
        long start = System.currentTimeMillis();

        QueryUnderstanding understanding = queryUnderstandingService.understand(question);
        HybridSearchResult hybridSearchResult = searchService.searchHybrid(question);
        String finalKeyword = StringUtils.hasText(understanding.getKeyword())
                ? understanding.getKeyword()
                : hybridSearchResult.getKeyword();

        AskContext askContext = contextBuilderService.buildContext(
                question,
                finalKeyword,
                hybridSearchResult.getMergedChunks()
        );
        List<ChunkHit> chunks = buildResponseChunks(askContext.getTopChunks(), finalKeyword);
        String answer = answerBuilderService.buildAnswer(askContext);

        AskResponse response = new AskResponse();
        response.setQuestion(question);
        response.setMode("BASELINE");
        response.setKeyword(finalKeyword);
        response.setChunks(chunks);
        response.setSources(buildSources(question, askContext.getTopChunks()));
        response.setRelationPaths(new ArrayList<>());
        response.setStructuredContext(askContext.getStructuredContext());
        response.setAnswer(answer);
        response.setQueryEntities(understanding.getQueryEntities());
        response.setKeywordHitCount(hybridSearchResult.getKeywordHits() == null ? 0 : hybridSearchResult.getKeywordHits().size());
        response.setEntityHitCount(hybridSearchResult.getEntityHits() == null ? 0 : hybridSearchResult.getEntityHits().size());
        response.setMergedChunkCount(hybridSearchResult.getMergedChunks() == null ? 0 : hybridSearchResult.getMergedChunks().size());
        response.setRetrievalMode("BASELINE_HYBRID");
        response.setDebug(buildDebugInfo("BASELINE_HYBRID",
                hybridSearchResult.getMergedChunks() == null ? 0 : hybridSearchResult.getMergedChunks().size(),
                0,
                chunks.size(),
                hybridSearchResult.getKeywordHits() == null ? 0 : hybridSearchResult.getKeywordHits().size(),
                hybridSearchResult.getEntityHits() == null ? 0 : hybridSearchResult.getEntityHits().size()));

        Long sourceDocId = chunks.isEmpty() ? null : chunks.get(0).getDocumentId();
        saveQueryLog(question, answer, sourceDocId, System.currentTimeMillis() - start);
        return response;
    }

    private AskResponse askRelationFirst(String question) {
        long start = System.currentTimeMillis();
        RelationSearchResult relationSearchResult = relationSearchService.searchRelationsWithDebug(question);
        List<RelationHit> relationHits = relationSearchResult.getRelationHits();
        if (relationHits == null || relationHits.isEmpty()) {
            return null;
        }

        AskResponse response = new AskResponse();
        response.setQuestion(question);
        response.setMode("GRAPH");
        response.setAnswer(buildRelationFirstAnswerV2(question, relationHits));
        response.setRelationPaths(buildRelationPathsFromHits(relationHits));
        response.setSources(buildSourcesFromRelationHits(question, relationHits));
        response.setChunks(new ArrayList<>());
        response.setStructuredContext(buildRelationStructuredContextV2(question, relationHits));
        response.setRetrievalMode("GRAPH");
        response.setDebug(buildDebugInfo("GRAPH",
                relationHits.size(),
                relationHits.size(),
                response.getSources().size(),
                0,
                0));
        response.getDebug().setExtractedEntityKeywords(relationSearchResult.getExtractedEntityKeywords());
        response.getDebug().setEntityCandidateCount(relationSearchResult.getEntityCandidateCount());

        Long sourceDocId = response.getSources().isEmpty() ? null : response.getSources().get(0).getDocumentId();
        saveQueryLog(question, response.getAnswer(), sourceDocId, System.currentTimeMillis() - start);
        return response;
    }

    private AskResponse askGraph(String question) {
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

        AskResponse response = new AskResponse();
        response.setQuestion(question);
        response.setMode("graph");
        response.setKeyword(finalKeyword);
        response.setChunks(chunks);
        response.setSources(buildSources(question, askContext.getTopChunks()));
        response.setRelationPaths(buildRelationPaths(relationHits));
        response.setStructuredContext(structuredContext == null ? askContext.getStructuredContext() : structuredContext.getSummary());
        response.setAnswer(answer);
        response.setQueryEntities(understanding.getQueryEntities());
        response.setKeywordHitCount(hybridSearchResult.getKeywordHits() == null ? 0 : hybridSearchResult.getKeywordHits().size());
        response.setEntityHitCount(hybridSearchResult.getEntityHits() == null ? 0 : hybridSearchResult.getEntityHits().size());
        response.setMergedChunkCount(hybridSearchResult.getMergedChunks() == null ? 0 : hybridSearchResult.getMergedChunks().size());
        response.setRetrievalMode("GRAPH_HYBRID");
        response.setDebug(buildDebugInfo("GRAPH_HYBRID",
                hybridSearchResult.getMergedChunks() == null ? 0 : hybridSearchResult.getMergedChunks().size(),
                relationHits.size(),
                chunks.size(),
                hybridSearchResult.getKeywordHits() == null ? 0 : hybridSearchResult.getKeywordHits().size(),
                hybridSearchResult.getEntityHits() == null ? 0 : hybridSearchResult.getEntityHits().size()));

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
                if (!shouldQueryRelationFallbackTerm(understanding, queryTerm)) {
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

    private boolean shouldQueryRelationFallbackTerm(QueryUnderstanding understanding, String queryTerm) {
        if (!StringUtils.hasText(queryTerm)) {
            return false;
        }
        if (understanding != null && understanding.getIntent() == QueryIntent.LIBRARY_DEPENDENCY) {
            return true;
        }
        return looksLikeEntityTerm(queryTerm);
    }

    private String normalizeMode(String mode) {
        if (!StringUtils.hasText(mode)) {
            return "graph";
        }
        String normalized = mode.trim().toLowerCase(Locale.ROOT);
        return "baseline".equals(normalized) ? "baseline" : "graph";
    }

    private boolean containsIgnoreCase(String text, String token) {
        return StringUtils.hasText(text) && StringUtils.hasText(token)
                && text.toLowerCase(Locale.ROOT).contains(token.toLowerCase(Locale.ROOT));
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

    private List<SourceItem> buildSources(String question, List<ChunkHit> hits) {
        List<SourceItem> sources = new ArrayList<>();
        if (hits == null || hits.isEmpty()) {
            return sources;
        }

        for (ChunkHit hit : hits) {
            SourceItem source = new SourceItem();
            source.setDocumentId(hit.getDocumentId());
            source.setChunkId(hit.getChunkId());
            source.setDocumentName(resolveDocumentName(hit.getDocumentId()));
            source.setContent(hit.getContent());
            source.setScore(hit.getChunkIndex() == null ? null : Math.max(0.0d, 100.0d - hit.getChunkIndex()));
            source.setSourceType("CHUNK");
            source.setEvidenceSentences(evidenceSelector.selectEvidenceSentences(question, hit.getContent()));
            sources.add(source);
        }
        return sources;
    }

    private List<RelationPathItem> buildRelationPaths(List<RelationQueryHit> relationHits) {
        List<RelationPathItem> relationPaths = new ArrayList<>();
        if (relationHits == null || relationHits.isEmpty()) {
            return relationPaths;
        }

        for (RelationQueryHit hit : relationHits) {
            RelationPathItem item = new RelationPathItem();
            item.setRelationType(hit.getRelationType());
            item.setTargetEntity(hit.getEntityName());
            item.setEvidenceText(hit.getEvidenceText());
            item.setDocumentId(hit.getDocumentId());
            item.setChunkId(hit.getChunkId());
            item.setSummary(buildRelationSummary(hit));
            relationPaths.add(item);
        }
        return relationPaths;
    }

    private List<RelationPathItem> buildRelationPathsFromHits(List<RelationHit> relationHits) {
        List<RelationPathItem> relationPaths = new ArrayList<>();
        if (relationHits == null || relationHits.isEmpty()) {
            return relationPaths;
        }
        for (RelationHit hit : relationHits) {
            RelationPathItem item = new RelationPathItem();
            item.setSourceEntity(hit.getSourceEntityName());
            item.setSourceEntityType(hit.getSourceEntityType());
            item.setRelationType(hit.getRelationType());
            item.setTargetEntity(hit.getTargetEntityName());
            item.setTargetEntityType(hit.getTargetEntityType());
            item.setEvidenceText(hit.getEvidenceText());
            item.setDocumentId(hit.getDocumentId());
            item.setChunkId(hit.getChunkId());
            item.setConfidence(hit.getConfidence());
            item.setSummary(buildRelationSummary(hit));
            relationPaths.add(item);
        }
        return relationPaths;
    }

    private List<SourceItem> buildSourcesFromRelationHits(String question, List<RelationHit> relationHits) {
        List<SourceItem> sources = new ArrayList<>();
        if (relationHits == null || relationHits.isEmpty()) {
            return sources;
        }
        for (RelationHit hit : relationHits) {
            String sourceContent = resolveRelationSourceContent(hit);
            SourceItem source = new SourceItem();
            source.setDocumentId(hit.getDocumentId());
            source.setChunkId(hit.getChunkId());
            source.setDocumentName(resolveDocumentName(hit.getDocumentId()));
            source.setContent(sourceContent);
            source.setScore(hit.getConfidence());
            source.setSourceType("RELATION");
            source.setEvidenceSentences(evidenceSelector.selectEvidenceSentences(question, sourceContent));
            sources.add(source);
        }
        return sources;
    }

    private String resolveRelationSourceContent(RelationHit hit) {
        if (hit == null) {
            return "";
        }
        if (StringUtils.hasText(hit.getEvidenceText())) {
            return hit.getEvidenceText();
        }
        if (hit.getChunkId() != null) {
            return documentChunkRepository.findById(hit.getChunkId())
                    .map(DocumentChunk::getContent)
                    .orElse("");
        }
        return "";
    }

    private String buildRelationSummary(RelationQueryHit hit) {
        String relationType = hit == null ? "" : hit.getRelationType();
        String entityName = hit == null ? "" : hit.getEntityName();
        if (!StringUtils.hasText(relationType) && !StringUtils.hasText(entityName)) {
            return "";
        }
        return relationType + " -> " + entityName;
    }

    private String buildRelationSummary(RelationHit hit) {
        if (hit == null) {
            return "";
        }
        return valueOrEmpty(hit.getSourceEntityName()) + " " +
                valueOrEmpty(hit.getRelationType()) + " " +
                valueOrEmpty(hit.getTargetEntityName());
    }

    private String buildRelationStructuredContext(List<RelationHit> relationHits) {
        StringBuilder builder = new StringBuilder();
        builder.append("relationHits=").append(relationHits == null ? 0 : relationHits.size());
        if (relationHits != null) {
            int limit = Math.min(5, relationHits.size());
            for (int i = 0; i < limit; i++) {
                builder.append("\n").append(i + 1).append(". ").append(buildRelationSummary(relationHits.get(i)));
            }
        }
        return builder.toString();
    }

    private String buildRelationFirstAnswer(List<RelationHit> relationHits) {
        if (relationHits == null || relationHits.isEmpty()) {
            return "";
        }
        RelationHit first = relationHits.get(0);
        StringBuilder builder = new StringBuilder();
        builder.append("结论：");
        builder.append(buildRelationSummary(first)).append("。");
        builder.append("\n\n命中关系：");
        int limit = Math.min(3, relationHits.size());
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                builder.append("；");
            }
            builder.append(buildRelationSummary(relationHits.get(i)));
        }
        builder.append("\n\n证据：");
        builder.append(StringUtils.hasText(first.getEvidenceText()) ? first.getEvidenceText() : "暂无证据文本。");
        return builder.toString();
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private String buildRelationStructuredContextV2(String question, List<RelationHit> relationHits) {
        if (isTableHasFieldQuestionV2(relationHits)) {
            return buildTableHasFieldStructuredContextV2(question, relationHits);
        }
        return buildRelationStructuredContext(relationHits);
    }

    private String buildRelationFirstAnswerV2(String question, List<RelationHit> relationHits) {
        if (relationHits == null || relationHits.isEmpty()) {
            return "";
        }
        if (isTableHasFieldQuestionV2(relationHits)) {
            return buildTableHasFieldAnswerV2(question, relationHits);
        }
        return buildRelationFirstAnswer(relationHits);
    }

    private boolean isTableHasFieldQuestionV2(List<RelationHit> relationHits) {
        return relationHits != null
                && !relationHits.isEmpty()
                && "TABLE_HAS_FIELD".equalsIgnoreCase(relationHits.get(0).getRelationType());
    }

    private String buildTableHasFieldAnswerV2(String question, List<RelationHit> relationHits) {
        RelationHit first = relationHits.get(0);
        String tableName = valueOrEmpty(first.getSourceEntityName());
        List<String> fields = extractTableFieldsV2(relationHits, tableName);

        StringBuilder builder = new StringBuilder();
        builder.append("结论：")
                .append(tableName)
                .append(" 表包含以下字段：")
                .append(String.join("、", fields))
                .append("。");

        builder.append("\n\n命中实体/关系：")
                .append(tableName)
                .append(" 的 TABLE_HAS_FIELD 关系命中 ")
                .append(fields.size())
                .append(" 条：")
                .append(String.join("、", fields))
                .append("。");

        builder.append("\n\n证据摘要：");
        List<String> evidences = extractUniqueEvidenceTextsV2(question, relationHits, 3);
        if (evidences.isEmpty()) {
            builder.append("暂无可用证据摘要。");
        } else {
            for (int i = 0; i < evidences.size(); i++) {
                if (i > 0) {
                    builder.append("\n");
                }
                builder.append(i + 1).append(". ").append(evidences.get(i));
            }
        }
        return builder.toString();
    }

    private String buildTableHasFieldStructuredContextV2(String question, List<RelationHit> relationHits) {
        RelationHit first = relationHits.get(0);
        String tableName = valueOrEmpty(first.getSourceEntityName());
        List<String> fields = extractTableFieldsV2(relationHits, tableName);
        StringBuilder builder = new StringBuilder();
        builder.append("relationHits=").append(relationHits.size());
        builder.append("\n").append(tableName).append(" fields=").append(fields);
        List<String> evidences = extractUniqueEvidenceTextsV2(question, relationHits, 3);
        for (int i = 0; i < evidences.size(); i++) {
            builder.append("\n").append(i + 1).append(". ").append(evidences.get(i));
        }
        return builder.toString();
    }

    private List<String> extractTableFieldsV2(List<RelationHit> relationHits, String tableName) {
        Set<String> fields = new LinkedHashSet<>();
        for (RelationHit hit : relationHits) {
            if (hit == null || !"TABLE_HAS_FIELD".equalsIgnoreCase(hit.getRelationType())) {
                continue;
            }
            if (!valueOrEmpty(tableName).equalsIgnoreCase(valueOrEmpty(hit.getSourceEntityName()))) {
                continue;
            }
            if (StringUtils.hasText(hit.getTargetEntityName())) {
                fields.add(hit.getTargetEntityName());
            }
        }
        return new ArrayList<>(fields);
    }

    private List<String> extractUniqueEvidenceTextsV2(String question, List<RelationHit> relationHits, int maxCount) {
        Set<String> evidences = new LinkedHashSet<>();
        boolean tableFieldQuestion = isTableFieldEvidenceQuestion(question);
        for (RelationHit hit : relationHits) {
            if (hit != null) {
                String sourceText = resolveRelationSourceContent(hit);
                List<String> sentences = evidenceSelector.selectEvidenceSentences(question, sourceText);
                if (!sentences.isEmpty()) {
                    evidences.addAll(sentences);
                } else if (!tableFieldQuestion && StringUtils.hasText(sourceText)) {
                    evidences.add(sourceText.trim());
                }
            }
            if (evidences.size() >= maxCount) {
                break;
            }
        }
        return new ArrayList<>(evidences);
    }

    private boolean isTableFieldEvidenceQuestion(String question) {
        if (!StringUtils.hasText(question)) {
            return false;
        }
        String lower = question.toLowerCase(Locale.ROOT);
        return lower.contains("表有哪些字段")
                || lower.contains("有哪些字段")
                || lower.contains("字段包括什么");
    }

    private AskDebugInfo buildDebugInfo(String retrievalType,
                                        int hitCount,
                                        int relationHitCount,
                                        int chunkHitCount,
                                        int keywordHitCount,
                                        int entityHitCount) {
        AskDebugInfo debugInfo = new AskDebugInfo();
        debugInfo.setRetrievalType(retrievalType);
        debugInfo.setHitCount(hitCount);
        debugInfo.setRelationHitCount(relationHitCount);
        debugInfo.setChunkHitCount(chunkHitCount);
        debugInfo.setKeywordHitCount(keywordHitCount);
        debugInfo.setEntityHitCount(entityHitCount);
        return debugInfo;
    }

    private void applyRouteDebug(AskResponse response,
                                 AgentRouteDecision routeDecision,
                                 List<ToolType> executedTools) {
        if (response == null) {
            return;
        }
        if (response.getDebug() == null) {
            response.setDebug(new AskDebugInfo());
        }
        response.getDebug().setRouteDecision(routeDecision);
        response.getDebug().setExecutedTools(executedTools);
    }

    private boolean containsSelectedTool(AgentRouteDecision routeDecision, ToolType toolType) {
        return routeDecision != null
                && routeDecision.getSelectedTools() != null
                && routeDecision.getSelectedTools().contains(toolType);
    }

    private List<ToolType> buildExecutedTools(ToolType... toolTypes) {
        List<ToolType> executedTools = new ArrayList<>();
        for (ToolType toolType : toolTypes) {
            executedTools.add(toolType);
        }
        return executedTools;
    }

    private AgentRouteDecision buildForcedBaselineRouteDecision(QuestionType questionType) {
        AgentRouteDecision decision = new AgentRouteDecision();
        decision.setQuestionType(questionType);
        decision.setSelectedTools(buildExecutedTools(ToolType.CHUNK_SEARCH, ToolType.ANSWER_GENERATE));
        decision.setReason("forced baseline mode uses chunk search and answer generation");
        return decision;
    }

    private String resolveDocumentName(Long documentId) {
        if (documentId == null) {
            return "";
        }
        return documentRepository.findById(documentId)
                .map(Document::getFileName)
                .orElse("");
    }

    private String buildSnippet(String content, String keyword) {
        if (!StringUtils.hasText(content)) {
            return content;
        }

        String trimmed = content.trim();
        if (!StringUtils.hasText(keyword)) {
            return buildSummary(trimmed, 120);
        }

        int index = trimmed.toLowerCase(Locale.ROOT).indexOf(keyword.toLowerCase(Locale.ROOT));
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
