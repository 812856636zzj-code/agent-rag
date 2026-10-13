package com.example.rag.service.impl;

import com.example.rag.dto.ChunkHit;
import com.example.rag.dto.LlmAnswerRequest;
import com.example.rag.dto.RelationHit;
import com.example.rag.dto.RelationPathItem;
import com.example.rag.dto.SourceItem;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * The answer boundary contains selected evidence, never the original retrieval text.
 * Retrieval diagnostics remain on the response, but they cannot become model context.
 */
final class SupportedEvidenceContext {
    private final List<SourceItem> sources;

    private SupportedEvidenceContext(List<SourceItem> sources) {
        this.sources = List.copyOf(sources);
    }

    static SupportedEvidenceContext from(List<SourceItem> candidates) {
        List<SourceItem> supported = new ArrayList<>();
        if (candidates != null) {
            for (SourceItem candidate : candidates) {
                SourceItem source = sanitizedSource(candidate);
                if (source != null) {
                    supported.add(source);
                }
            }
        }
        return new SupportedEvidenceContext(supported);
    }

    boolean isEmpty() {
        return sources.isEmpty();
    }

    List<SourceItem> sources() {
        return new ArrayList<>(sources);
    }

    String answer() {
        Set<String> distinct = new LinkedHashSet<>();
        for (SourceItem source : sources) {
            distinct.addAll(source.getEvidenceSentences());
        }
        return String.join("；", distinct);
    }

    String structuredContext() {
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < sources.size(); i++) {
            SourceItem source = sources.get(i);
            if (i > 0) {
                context.append('\n');
            }
            context.append(i + 1).append(". ").append(safeType(source.getSourceType()));
            if (source.getDocumentId() != null) {
                context.append(" documentId=").append(source.getDocumentId());
            }
            if (source.getChunkId() != null) {
                context.append(" chunkId=").append(source.getChunkId());
            }
            context.append(": ").append(source.getContent());
        }
        return context.toString();
    }

    LlmAnswerRequest request(String question, String traceContext) {
        LlmAnswerRequest request = new LlmAnswerRequest();
        request.setQuestion(question);
        request.setSources(sources());
        request.setStructuredContext(structuredContext());
        request.setRuleBasedAnswer(answer());
        request.setTraceContext(traceContext);

        List<ChunkHit> chunks = new ArrayList<>();
        List<RelationHit> relations = new ArrayList<>();
        for (SourceItem source : sources) {
            if ("CHUNK".equalsIgnoreCase(source.getSourceType())) {
                ChunkHit hit = new ChunkHit();
                hit.setChunkId(source.getChunkId());
                hit.setDocumentId(source.getDocumentId());
                hit.setContent(source.getContent());
                chunks.add(hit);
            } else if ("RELATION".equalsIgnoreCase(source.getSourceType())) {
                RelationHit hit = new RelationHit();
                hit.setChunkId(source.getChunkId());
                hit.setDocumentId(source.getDocumentId());
                hit.setEvidenceText(source.getContent());
                relations.add(hit);
            }
        }
        request.setTopChunks(chunks);
        request.setRelationEvidence(relations);
        return request;
    }

    List<RelationPathItem> supportedRelationPaths(List<RelationPathItem> candidates) {
        List<RelationPathItem> supported = new ArrayList<>();
        if (candidates == null) {
            return supported;
        }
        for (RelationPathItem path : candidates) {
            if (path == null) {
                continue;
            }
            for (SourceItem source : sources) {
                if (!"RELATION".equalsIgnoreCase(source.getSourceType())) {
                    continue;
                }
                if (sameProvenance(path, source) && supportsEntities(path, source.getContent())) {
                    supported.add(copyPathWithEvidence(path, source.getContent()));
                    break;
                }
            }
        }
        return supported;
    }

    private static SourceItem sanitizedSource(SourceItem candidate) {
        if (candidate == null || candidate.getEvidenceSentences() == null) {
            return null;
        }
        Set<String> sentences = new LinkedHashSet<>();
        for (String sentence : candidate.getEvidenceSentences()) {
            if (StringUtils.hasText(sentence)) {
                sentences.add(sentence.trim());
            }
        }
        if (sentences.isEmpty()) {
            return null;
        }
        SourceItem source = new SourceItem();
        source.setDocumentId(candidate.getDocumentId());
        source.setChunkId(candidate.getChunkId());
        source.setDocumentName(candidate.getDocumentName());
        source.setScore(candidate.getScore());
        source.setSourceType(candidate.getSourceType());
        source.setEvidenceSentences(new ArrayList<>(sentences));
        source.setContent(String.join(" ", sentences));
        return source;
    }

    private static boolean sameProvenance(RelationPathItem path, SourceItem source) {
        boolean matched = false;
        if (source.getChunkId() != null && path.getChunkId() != null) {
            if (!Objects.equals(source.getChunkId(), path.getChunkId())) {
                return false;
            }
            matched = true;
        }
        if (source.getDocumentId() != null && path.getDocumentId() != null) {
            if (!Objects.equals(source.getDocumentId(), path.getDocumentId())) {
                return false;
            }
            matched = true;
        }
        return matched;
    }

    private static boolean supportsEntities(RelationPathItem path, String evidence) {
        String lower = evidence.toLowerCase(Locale.ROOT);
        return (!StringUtils.hasText(path.getSourceEntity())
                    || lower.contains(path.getSourceEntity().toLowerCase(Locale.ROOT)))
                && (!StringUtils.hasText(path.getTargetEntity())
                    || lower.contains(path.getTargetEntity().toLowerCase(Locale.ROOT)));
    }

    private static RelationPathItem copyPathWithEvidence(RelationPathItem path, String evidence) {
        RelationPathItem copy = new RelationPathItem();
        copy.setSourceEntity(path.getSourceEntity());
        copy.setSourceEntityType(path.getSourceEntityType());
        copy.setRelationType(path.getRelationType());
        copy.setTargetEntity(path.getTargetEntity());
        copy.setTargetEntityType(path.getTargetEntityType());
        copy.setDocumentId(path.getDocumentId());
        copy.setChunkId(path.getChunkId());
        copy.setConfidence(path.getConfidence());
        copy.setEvidenceText(evidence);
        copy.setSummary(evidence);
        return copy;
    }

    private static String safeType(String type) {
        if ("CHUNK".equalsIgnoreCase(type)) {
            return "CHUNK";
        }
        if ("RELATION".equalsIgnoreCase(type)) {
            return "RELATION";
        }
        if ("SQL_METADATA".equalsIgnoreCase(type)) {
            return "SQL_METADATA";
        }
        return "EVIDENCE";
    }
}
