package com.example.rag.service.impl;

import com.example.rag.dto.AskContext;
import com.example.rag.dto.ChunkHit;
import com.example.rag.dto.HybridSearchResult;
import com.example.rag.dto.QueryEntity;
import com.example.rag.dto.QueryUnderstanding;
import com.example.rag.dto.RelationQueryHit;
import com.example.rag.dto.ScoredSentence;
import com.example.rag.dto.SearchResult;
import com.example.rag.dto.StructuredContext;
import com.example.rag.enums.QueryIntent;
import com.example.rag.service.ContextBuilderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class ContextBuilderServiceImpl implements ContextBuilderService {

    private static final Logger log = LoggerFactory.getLogger(ContextBuilderServiceImpl.class);
    private static final int TOP_CHUNK_LIMIT = 5;
    private static final int HINT_LIMIT = 5;
    private static final int EVIDENCE_MAX_LENGTH = 200;
    private static final Pattern SENTENCE_SPLIT_PATTERN = Pattern.compile("[\u3002\\n\uff1b;\uff1a:]|(?<=[A-Za-z0-9_])\\.(?=\\s|$)");

    @Override
    public AskContext buildContext(String question, String keyword, List<ChunkHit> matchedChunks) {
        QueryUnderstanding understanding = new QueryUnderstanding();
        understanding.setQuestion(question);
        understanding.setKeyword(keyword);
        understanding.setIntent(QueryIntent.GENERIC);

        HybridSearchResult hybridSearchResult = new HybridSearchResult();
        hybridSearchResult.setQuestion(question);
        hybridSearchResult.setKeyword(keyword);
        hybridSearchResult.setMergedChunks(matchedChunks == null ? new ArrayList<>() : new ArrayList<>(matchedChunks));
        hybridSearchResult.setQueryEntities(new ArrayList<>());
        return buildGraphContext(understanding, hybridSearchResult, new ArrayList<>());
    }

    @Override
    public AskContext buildContext(String question, String keyword, SearchResult searchResult) {
        List<ChunkHit> matchedChunks = searchResult == null ? new ArrayList<>() : searchResult.getMatchedChunks();
        return buildContext(question, keyword, matchedChunks);
    }

    @Override
    public AskContext buildGraphContext(QueryUnderstanding understanding,
                                        HybridSearchResult hybridSearchResult,
                                        List<RelationQueryHit> relationHits) {
        String question = understanding == null ? "" : understanding.getQuestion();
        String keyword = understanding == null ? "" : understanding.getKeyword();
        log.info("buildGraphContext start, keyword = {}", keyword);

        AskContext context = new AskContext();
        context.setQuestion(question);
        context.setKeyword(keyword);

        List<ChunkHit> mergedChunks = hybridSearchResult == null || hybridSearchResult.getMergedChunks() == null
                ? new ArrayList<>()
                : new ArrayList<>(hybridSearchResult.getMergedChunks());
        List<RelationQueryHit> safeRelationHits = relationHits == null ? new ArrayList<>() : dedupRelations(relationHits);

        context.setMatchedChunks(mergedChunks);
        context.setTopChunks(buildTopChunks(mergedChunks));
        context.setSourceDocIds(buildSourceDocIds(context.getTopChunks()));

        List<String> answerHints = buildAnswerHints(understanding, context.getTopChunks(), safeRelationHits);
        context.setAnswerHints(answerHints);

        StructuredContext structuredContextData = new StructuredContext();
        structuredContextData.setQuestion(question);
        structuredContextData.setKeyword(keyword);
        structuredContextData.setIntent(understanding == null || understanding.getIntent() == null ? null : understanding.getIntent().name());
        structuredContextData.setFocusEntity(understanding == null ? null : understanding.getFocusEntity());
        structuredContextData.setMatchedEntities(resolveMatchedEntityNames(understanding, hybridSearchResult));
        structuredContextData.setMatchedRelations(resolveMatchedRelationTexts(understanding == null ? null : understanding.getFocusEntity(), safeRelationHits));
        structuredContextData.setRetrievedChunks(context.getTopChunks());
        structuredContextData.setEvidenceTexts(new ArrayList<>(answerHints));
        structuredContextData.setSummary(buildSummary(understanding, mergedChunks, safeRelationHits));
        context.setStructuredContextData(structuredContextData);
        context.setStructuredContext(buildStructuredContextText(question, keyword, mergedChunks.size(), safeRelationHits.size(), answerHints));
        return context;
    }

    private List<String> resolveMatchedEntityNames(QueryUnderstanding understanding, HybridSearchResult hybridSearchResult) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        if (understanding != null && understanding.getQueryEntities() != null && !understanding.getQueryEntities().isEmpty()) {
            for (QueryEntity queryEntity : understanding.getQueryEntities()) {
                if (queryEntity != null && StringUtils.hasText(queryEntity.getText())) {
                    names.add(queryEntity.getText());
                }
            }
        }
        if (hybridSearchResult != null && hybridSearchResult.getQueryEntities() != null) {
            for (QueryEntity queryEntity : hybridSearchResult.getQueryEntities()) {
                if (queryEntity != null && StringUtils.hasText(queryEntity.getText())) {
                    names.add(queryEntity.getText());
                }
            }
        }
        return new ArrayList<>(names);
    }

    private List<String> resolveMatchedRelationTexts(String focusEntity, List<RelationQueryHit> relationHits) {
        LinkedHashSet<String> texts = new LinkedHashSet<>();
        for (RelationQueryHit relationHit : relationHits) {
            if (texts.size() >= 10) {
                break;
            }
            if (relationHit == null) {
                continue;
            }
            String entityName = relationHit.getEntityName();
            String relationType = relationHit.getRelationType();
            if (StringUtils.hasText(entityName) || StringUtils.hasText(relationType)) {
                if (StringUtils.hasText(focusEntity)) {
                    texts.add(focusEntity + " " + String.valueOf(relationType) + " " + String.valueOf(entityName));
                } else {
                    texts.add(String.valueOf(entityName) + " " + String.valueOf(relationType));
                }
            }
        }
        return new ArrayList<>(texts);
    }

    private List<ChunkHit> buildTopChunks(List<ChunkHit> matchedChunks) {
        int max = Math.min(TOP_CHUNK_LIMIT, matchedChunks.size());
        List<ChunkHit> topChunks = new ArrayList<>();
        for (int i = 0; i < max; i++) {
            topChunks.add(matchedChunks.get(i));
        }
        return topChunks;
    }

    private List<Long> buildSourceDocIds(List<ChunkHit> topChunks) {
        LinkedHashSet<Long> sourceDocIds = new LinkedHashSet<>();
        for (ChunkHit chunk : topChunks) {
            if (chunk != null && chunk.getDocumentId() != null) {
                sourceDocIds.add(chunk.getDocumentId());
            }
        }
        return new ArrayList<>(sourceDocIds);
    }

    private List<String> buildAnswerHints(QueryUnderstanding understanding,
                                          List<ChunkHit> topChunks,
                                          List<RelationQueryHit> relationHits) {
        List<ScoredSentence> candidates = new ArrayList<>();
        Set<String> focusTerms = buildFocusTerms(understanding);

        for (RelationQueryHit relationHit : relationHits) {
            if (!StringUtils.hasText(relationHit.getEvidenceText())) {
                continue;
            }
            String normalized = normalizeSentence(relationHit.getEvidenceText());
            int score = scoreSentence(understanding, normalized, focusTerms) + 5;
            if (StringUtils.hasText(relationHit.getRelationType())) {
                score += 2;
            }
            candidates.add(new ScoredSentence(clipEvidence(normalized), score));
        }

        for (ChunkHit chunk : topChunks) {
            if (chunk == null || !StringUtils.hasText(chunk.getContent())) {
                continue;
            }
            List<String> sentences = splitSentences(chunk.getContent());
            boolean matched = false;
            for (String sentence : sentences) {
                String normalized = normalizeSentence(sentence);
                if (!StringUtils.hasText(normalized)) {
                    continue;
                }
                int score = scoreSentence(understanding, normalized, focusTerms);
                if (score <= 0) {
                    continue;
                }
                matched = true;
                candidates.add(new ScoredSentence(clipEvidence(normalized), score));
            }
            if (!matched) {
                String fallback = clipEvidence(normalizeSentence(chunk.getContent()));
                if (StringUtils.hasText(fallback)) {
                    candidates.add(new ScoredSentence(fallback, 1));
                }
            }
        }

        candidates.sort(Comparator.comparingInt(ScoredSentence::getScore).reversed());
        LinkedHashSet<String> dedup = new LinkedHashSet<>();
        for (ScoredSentence candidate : candidates) {
            if (dedup.size() >= HINT_LIMIT) {
                break;
            }
            if (StringUtils.hasText(candidate.getText())) {
                dedup.add(candidate.getText());
            }
        }
        return new ArrayList<>(dedup);
    }

    private Set<String> buildFocusTerms(QueryUnderstanding understanding) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        if (understanding == null) {
            return terms;
        }
        addFocusTerm(terms, understanding.getFocusEntity());
        addFocusTerm(terms, understanding.getKeyword());
        if (understanding.getQueryTerms() != null) {
            for (String queryTerm : understanding.getQueryTerms()) {
                addFocusTerm(terms, queryTerm);
            }
        }
        if (understanding.getBoostTerms() != null) {
            for (String boostTerm : understanding.getBoostTerms()) {
                addFocusTerm(terms, boostTerm);
            }
        }
        if (understanding.getQueryEntities() != null) {
            for (QueryEntity queryEntity : understanding.getQueryEntities()) {
                if (queryEntity == null) {
                    continue;
                }
                addFocusTerm(terms, queryEntity.getText());
                addFocusTerm(terms, queryEntity.getNormalizedText());
            }
        }
        return terms;
    }

    private void addFocusTerm(Set<String> terms, String value) {
        if (StringUtils.hasText(value)) {
            terms.add(value.trim());
        }
    }

    private int scoreSentence(QueryUnderstanding understanding, String sentence, Set<String> focusTerms) {
        String lowerSentence = sentence.toLowerCase(Locale.ROOT);
        int score = 0;

        for (String focusTerm : focusTerms) {
            if (StringUtils.hasText(focusTerm) && lowerSentence.contains(focusTerm.toLowerCase(Locale.ROOT))) {
                score += 3;
            }
        }

        QueryIntent intent = understanding == null || understanding.getIntent() == null ? QueryIntent.GENERIC : understanding.getIntent();
        if (QueryIntent.TABLE_OWNERSHIP == intent && containsAny(lowerSentence, "rag_document_chunks", "embedding_status", "embeddingstatus")) {
            score += 6;
        }
        if (QueryIntent.LIBRARY_DEPENDENCY == intent && containsAny(lowerSentence, "pdfbox", "pdf", "library", "documentchunkservice", "uploadservice")) {
            score += 6;
        }
        if (QueryIntent.SERVICE_DEPENDENCY == intent && containsAny(lowerSentence, "searchservice", "contextbuilderservice", "answerbuilderservice", "relationqueryservice", "queryunderstandingservice")) {
            score += 6;
        }
        if (QueryIntent.SEARCH_RULE == intent && containsAny(lowerSentence, "\u6392\u5e8f", "\u4f18\u5148", "chunkindex", "documentid", "entity")) {
            score += 6;
        }
        if (QueryIntent.RELATION_QUERY == intent && containsAny(lowerSentence, "uses", "depends_on", "related_to", "service")) {
            score += 5;
        }
        if (QueryIntent.DEFINITION_LOCATION == intent && containsAny(lowerSentence, "chunk_size", "500", "documentchunkservice", "savechunks")) {
            score += 5;
        }
        if (QueryIntent.TABLE_OWNERSHIP == intent && containsAny(lowerSentence, "pdfbox", "/upload")) {
            score -= 4;
        }

        return score;
    }

    private List<String> splitSentences(String content) {
        List<String> sentences = new ArrayList<>();
        if (!StringUtils.hasText(content)) {
            return sentences;
        }
        String normalized = content.replace("\r", "\n");
        String[] parts = SENTENCE_SPLIT_PATTERN.split(normalized);
        for (String part : parts) {
            String sentence = normalizeSentence(part);
            if (StringUtils.hasText(sentence)) {
                sentences.add(sentence);
            }
        }
        return sentences;
    }

    private String normalizeSentence(String sentence) {
        if (!StringUtils.hasText(sentence)) {
            return "";
        }
        return sentence.replaceAll("\\s+", " ").trim();
    }

    private String clipEvidence(String sentence) {
        if (!StringUtils.hasText(sentence)) {
            return "";
        }
        if (sentence.length() <= EVIDENCE_MAX_LENGTH) {
            return sentence;
        }
        int end = sentence.lastIndexOf(' ', EVIDENCE_MAX_LENGTH);
        if (end < EVIDENCE_MAX_LENGTH / 2) {
            end = EVIDENCE_MAX_LENGTH;
        }
        return sentence.substring(0, end).trim() + "...";
    }

    private List<RelationQueryHit> dedupRelations(List<RelationQueryHit> relationHits) {
        Map<String, RelationQueryHit> dedup = new LinkedHashMap<>();
        for (RelationQueryHit relationHit : relationHits) {
            String key = String.valueOf(relationHit.getEntityName()) + "|" +
                    String.valueOf(relationHit.getRelationType()) + "|" +
                    String.valueOf(relationHit.getChunkId());
            dedup.putIfAbsent(key, relationHit);
        }
        return new ArrayList<>(dedup.values());
    }

    private String buildSummary(QueryUnderstanding understanding,
                                List<ChunkHit> mergedChunks,
                                List<RelationQueryHit> relationHits) {
        StringBuilder sb = new StringBuilder();
        sb.append("question=").append(understanding == null ? "" : valueOrEmpty(understanding.getQuestion())).append("\n");
        sb.append("keyword=").append(understanding == null ? "" : valueOrEmpty(understanding.getKeyword())).append("\n");
        sb.append("intent=").append(understanding == null || understanding.getIntent() == null ? QueryIntent.GENERIC.name() : understanding.getIntent().name()).append("\n");
        sb.append("focusEntity=").append(understanding == null ? "" : valueOrEmpty(understanding.getFocusEntity())).append("\n");
        sb.append("matchedRelations=").append(Math.min(relationHits.size(), 10)).append("\n");
        sb.append("retrievedChunks=").append(mergedChunks.size()).append("\n");
        sb.append("evidencePriority=relationHits.evidenceText->chunkContent");
        return sb.toString();
    }

    private String buildStructuredContextText(String question,
                                              String keyword,
                                              int chunkCount,
                                              int relationCount,
                                              List<String> answerHints) {
        StringBuilder sb = new StringBuilder();
        sb.append("\u95ee\u9898\uff1a").append(valueOrEmpty(question)).append("\n");
        sb.append("\u5173\u952e\u8bcd\uff1a").append(valueOrEmpty(keyword)).append("\n");
        sb.append("\u547d\u4e2d\u5757\u6570\uff1a").append(chunkCount).append("\n");
        sb.append("\u5173\u7cfb\u547d\u4e2d\u6570\uff1a").append(relationCount).append("\n");
        sb.append("\u4e3b\u8981\u8bc1\u636e\uff1a");
        if (answerHints.isEmpty()) {
            sb.append("\n").append("\u6682\u672a\u68c0\u7d22\u5230\u53ef\u7528\u8bc1\u636e\u3002");
            return sb.toString();
        }
        for (int i = 0; i < answerHints.size(); i++) {
            sb.append("\n").append(i + 1).append(". ").append(answerHints.get(i));
        }
        return sb.toString();
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private boolean containsAny(String text, String... tokens) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String token : tokens) {
            if (lower.contains(token.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
