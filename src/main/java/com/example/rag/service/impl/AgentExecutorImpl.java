package com.example.rag.service.impl;

import com.example.rag.dto.AgentExecutionStep;
import com.example.rag.dto.AgentExecutionTrace;
import com.example.rag.dto.AgentMemoryItem;
import com.example.rag.dto.AgentRouteDecision;
import com.example.rag.dto.AgentToolInput;
import com.example.rag.dto.AgentToolResult;
import com.example.rag.dto.AskDebugInfo;
import com.example.rag.dto.AskResponse;
import com.example.rag.dto.ChunkHit;
import com.example.rag.dto.QueryEntity;
import com.example.rag.dto.QueryUnderstanding;
import com.example.rag.dto.RelationHit;
import com.example.rag.dto.SqlTableColumnResult;
import com.example.rag.enums.QuestionType;
import com.example.rag.enums.ToolType;
import com.example.rag.service.AgentExecutor;
import com.example.rag.service.AgentTool;
import com.example.rag.service.MemoryService;
import com.example.rag.service.QueryUnderstandingService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AgentExecutorImpl implements AgentExecutor {

    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("\\b[A-Z][A-Z0-9_]*\\b");

    private final GraphSearchTool graphSearchTool;
    private final ChunkSearchTool chunkSearchTool;
    private final SqlSearchTool sqlSearchTool;
    private final AnswerGenerateTool answerGenerateTool;
    private final QueryUnderstandingService queryUnderstandingService;
    private final MemoryService memoryService;

    public AgentExecutorImpl(GraphSearchTool graphSearchTool,
                             ChunkSearchTool chunkSearchTool,
                             SqlSearchTool sqlSearchTool,
                             AnswerGenerateTool answerGenerateTool,
                             QueryUnderstandingService queryUnderstandingService,
                             MemoryService memoryService) {
        this.graphSearchTool = graphSearchTool;
        this.chunkSearchTool = chunkSearchTool;
        this.sqlSearchTool = sqlSearchTool;
        this.answerGenerateTool = answerGenerateTool;
        this.queryUnderstandingService = queryUnderstandingService;
        this.memoryService = memoryService;
    }

    @Override
    public AskResponse execute(String question, AgentRouteDecision routeDecision) {
        AgentExecutionTrace trace = new AgentExecutionTrace();
        trace.setTraceId(UUID.randomUUID().toString());
        QueryUnderstanding understanding = safeUnderstand(question);
        List<AgentMemoryItem> relevantMemories = memoryService.findRelevantMemories(
                question,
                understanding == null || understanding.getIntent() == null ? null : understanding.getIntent().name(),
                extractDetectedEntities(understanding),
                null
        );
        trace.setMemoriesQueried(relevantMemories.size());
        trace.setMemoriesUsed(relevantMemories);
        trace.setMemoryMatchReason(buildMemoryMatchReason(relevantMemories));

        AgentToolInput input = new AgentToolInput();
        input.setQuestion(question);
        input.setQuestionType(routeDecision == null ? QuestionType.UNKNOWN : routeDecision.getQuestionType());
        input.setRouteDecision(routeDecision);

        List<RelationHit> relationHits = new ArrayList<>();
        List<ChunkHit> chunkHits = new ArrayList<>();
        AskResponse response;

        if (isRelationRoute(routeDecision)) {
            AgentToolResult graphResult = executeTool(graphSearchTool, input, trace);
            relationHits = extractRelationHits(graphResult);
            input.setRelationHits(relationHits);

            if (shouldUseGraphAnswer(question, routeDecision, relationHits)) {
                AgentToolResult answerResult = executeTool(answerGenerateTool, input, trace);
                response = extractAskResponse(answerResult);
                if (response == null) {
                    response = answerGenerateToolFallback(question, "AGENT_GRAPH", "AGENT_GRAPH");
                } else {
                    normalizeAgentGraphResponse(response);
                }
            } else if (usesSqlSearch(routeDecision)) {
                AgentToolResult sqlResult = executeTool(sqlSearchTool, input, trace);
                SqlTableColumnResult sqlMetadata = extractSqlMetadata(sqlResult);
                input.setContext(sqlMetadata);
                AgentToolResult answerResult = executeTool(answerGenerateTool, input, trace);
                response = extractAskResponse(answerResult);
                if (response == null) {
                    response = answerGenerateToolFallback(question, "AGENT_GRAPH", "AGENT_GRAPH");
                } else {
                    normalizeAgentGraphResponse(response);
                }
            } else {
                AgentToolResult chunkResult = executeTool(chunkSearchTool, input, trace);
                chunkHits = extractChunkHits(chunkResult);
                input.setChunkHits(chunkHits);
                AgentToolResult answerResult = executeTool(answerGenerateTool, input, trace);
                response = extractAskResponse(answerResult);
                if (response == null) {
                    response = answerGenerateToolFallback(question, "BASELINE_FALLBACK", "BASELINE_FALLBACK");
                } else {
                    normalizeFallbackResponse(response);
                }
            }
        } else {
            AgentToolResult chunkResult = executeTool(chunkSearchTool, input, trace);
            chunkHits = extractChunkHits(chunkResult);
            input.setChunkHits(chunkHits);
            AgentToolResult answerResult = executeTool(answerGenerateTool, input, trace);
            response = extractAskResponse(answerResult);
            if (response == null) {
                response = answerGenerateToolFallback(question, "BASELINE", "BASELINE");
            } else {
                normalizeBaselineResponse(response);
            }
        }

        applyTrace(response, routeDecision, trace);
        memoryService.saveFromTrace(trace, response, understanding);
        return response;
    }

    private AgentToolResult executeTool(AgentTool tool, AgentToolInput input, AgentExecutionTrace trace) {
        AgentToolResult result = tool.execute(input);
        AgentExecutionStep step = new AgentExecutionStep();
        step.setStepIndex(trace.getSteps().size() + 1);
        step.setToolType(tool.type());
        step.setSuccess(result.isSuccess());
        step.setInputSummary(buildInputSummary(input));
        step.setOutputSummary(result.getSummary());
        step.setErrorMessage(result.getErrorMessage());
        step.setLatencyMs(result.getLatencyMs());
        trace.getSteps().add(step);
        return result;
    }

    private boolean isRelationRoute(AgentRouteDecision routeDecision) {
        return routeDecision != null
                && routeDecision.getQuestionType() == QuestionType.RELATION
                && routeDecision.getSelectedTools() != null
                && routeDecision.getSelectedTools().contains(ToolType.GRAPH_SEARCH);
    }

    @SuppressWarnings("unchecked")
    private List<RelationHit> extractRelationHits(AgentToolResult result) {
        if (result != null && result.isSuccess() && result.getData() instanceof List) {
            return (List<RelationHit>) result.getData();
        }
        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private List<ChunkHit> extractChunkHits(AgentToolResult result) {
        if (result != null && result.isSuccess() && result.getData() instanceof List) {
            return (List<ChunkHit>) result.getData();
        }
        return new ArrayList<>();
    }

    private AskResponse extractAskResponse(AgentToolResult result) {
        if (result != null && result.isSuccess() && result.getData() instanceof AskResponse) {
            return (AskResponse) result.getData();
        }
        return null;
    }

    private SqlTableColumnResult extractSqlMetadata(AgentToolResult result) {
        if (result != null && result.isSuccess() && result.getData() instanceof SqlTableColumnResult) {
            return (SqlTableColumnResult) result.getData();
        }
        return null;
    }

    private void applyTrace(AskResponse response,
                            AgentRouteDecision routeDecision,
                            AgentExecutionTrace trace) {
        if (response.getDebug() == null) {
            response.setDebug(new AskDebugInfo());
        }
        response.getDebug().setRouteDecision(routeDecision);
        response.getDebug().setExecutionTrace(trace);
        response.getDebug().setExecutedTools(extractExecutedTools(trace));
        response.getDebug().setMemoriesQueried(trace == null ? 0 : trace.getMemoriesQueried());
        response.getDebug().setMemoriesUsed(trace == null ? new ArrayList<>() : trace.getMemoriesUsed());
        response.getDebug().setMemoryMatchReason(trace == null ? "" : trace.getMemoryMatchReason());
    }

    private List<ToolType> extractExecutedTools(AgentExecutionTrace trace) {
        List<ToolType> executedTools = new ArrayList<>();
        if (trace == null || trace.getSteps() == null) {
            return executedTools;
        }
        for (AgentExecutionStep step : trace.getSteps()) {
            if (step != null && step.getToolType() != null) {
                executedTools.add(step.getToolType());
            }
        }
        return executedTools;
    }

    private String buildInputSummary(AgentToolInput input) {
        if (input == null) {
            return "";
        }
        int relationCount = input.getRelationHits() == null ? 0 : input.getRelationHits().size();
        int chunkCount = input.getChunkHits() == null ? 0 : input.getChunkHits().size();
        return "question=" + (input.getQuestion() == null ? "" : input.getQuestion())
                + ", questionType=" + input.getQuestionType()
                + ", relationHits=" + relationCount
                + ", chunkHits=" + chunkCount;
    }

    private QueryUnderstanding safeUnderstand(String question) {
        try {
            return queryUnderstandingService.understand(question);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private List<String> extractDetectedEntities(QueryUnderstanding understanding) {
        List<String> entities = new ArrayList<>();
        if (understanding == null) {
            return entities;
        }
        if (StringUtils.hasText(understanding.getFocusEntity())) {
            entities.add(understanding.getFocusEntity());
        }
        if (understanding.getQueryEntities() != null) {
            for (QueryEntity entity : understanding.getQueryEntities()) {
                if (entity == null) {
                    continue;
                }
                if (StringUtils.hasText(entity.getText())) {
                    entities.add(entity.getText());
                } else if (StringUtils.hasText(entity.getNormalizedText())) {
                    entities.add(entity.getNormalizedText());
                }
            }
        }
        return entities;
    }

    private String buildMemoryMatchReason(List<AgentMemoryItem> memories) {
        if (memories == null || memories.isEmpty()) {
            return "no relevant memory matched";
        }
        List<String> reasons = new ArrayList<>();
        for (AgentMemoryItem memory : memories) {
            if (memory == null || !StringUtils.hasText(memory.getMemoryMatchReason())) {
                continue;
            }
            if (!reasons.contains(memory.getMemoryMatchReason())) {
                reasons.add(memory.getMemoryMatchReason());
            }
        }
        return reasons.isEmpty() ? "memory matched by relevance score" : String.join("; ", reasons);
    }

    private boolean usesSqlSearch(AgentRouteDecision routeDecision) {
        return routeDecision != null
                && routeDecision.getSelectedTools() != null
                && routeDecision.getSelectedTools().contains(ToolType.SQL_SEARCH);
    }

    private boolean shouldUseGraphAnswer(String question,
                                         AgentRouteDecision routeDecision,
                                         List<RelationHit> relationHits) {
        if (relationHits == null || relationHits.isEmpty()) {
            return false;
        }
        if (!usesSqlSearch(routeDecision) || !isTableFieldQuestion(question)) {
            return true;
        }
        return hasValidTableHasFieldRelation(question, relationHits);
    }

    private boolean isTableFieldQuestion(String question) {
        if (!StringUtils.hasText(question)) {
            return false;
        }
        String normalized = question.toLowerCase(Locale.ROOT);
        return normalized.contains("\u8868\u6709\u54ea\u4e9b\u5b57\u6bb5")
                || normalized.contains("\u8868\u5305\u542b\u54ea\u4e9b\u5b57\u6bb5")
                || normalized.contains("\u6709\u54ea\u4e9b\u5217")
                || normalized.contains("\u8868\u5b57\u6bb5")
                || normalized.contains("\u5b57\u6bb5\u662f\u4ec0\u4e48");
    }

    private boolean hasValidTableHasFieldRelation(String question, List<RelationHit> relationHits) {
        String targetTableName = extractTargetTableName(question);
        if (!StringUtils.hasText(targetTableName) || relationHits == null || relationHits.isEmpty()) {
            return false;
        }
        for (RelationHit hit : relationHits) {
            if (hit == null) {
                continue;
            }
            if (!"TABLE_HAS_FIELD".equalsIgnoreCase(hit.getRelationType())) {
                continue;
            }
            if (!targetTableName.equalsIgnoreCase(valueOrEmpty(hit.getSourceEntityName()))) {
                continue;
            }
            if (StringUtils.hasText(hit.getTargetEntityName())) {
                return true;
            }
        }
        return false;
    }

    private String extractTargetTableName(String question) {
        if (!StringUtils.hasText(question)) {
            return "";
        }
        String upper = question.toUpperCase(Locale.ROOT);
        Matcher matcher = TABLE_NAME_PATTERN.matcher(upper);
        while (matcher.find()) {
            String candidate = matcher.group();
            if (isSafeTableName(candidate)) {
                return candidate;
            }
        }
        return "";
    }

    private boolean isSafeTableName(String candidate) {
        return StringUtils.hasText(candidate)
                && !"USER_TAB_COLUMNS".equals(candidate)
                && !"COLUMN_NAME".equals(candidate)
                && !"SELECT".equals(candidate)
                && !"FROM".equals(candidate)
                && !"WHERE".equals(candidate)
                && !"ORDER".equals(candidate)
                && !"BY".equals(candidate);
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private AskResponse answerGenerateToolFallback(String question, String mode, String retrievalMode) {
        AskResponse response = new AskResponse();
        response.setQuestion(question);
        response.setMode(mode);
        response.setKeyword("");
        response.setChunks(new ArrayList<>());
        response.setSources(new ArrayList<>());
        response.setRelationPaths(new ArrayList<>());
        response.setStructuredContext("");
        response.setAnswer("未检索到足够相关的资料，暂时无法基于知识库回答该问题。");
        response.setKeywordHitCount(0);
        response.setEntityHitCount(0);
        response.setMergedChunkCount(0);
        response.setRetrievalMode(retrievalMode);
        AskDebugInfo debugInfo = new AskDebugInfo();
        debugInfo.setRetrievalType(retrievalMode);
        debugInfo.setHitCount(0);
        debugInfo.setRelationHitCount(0);
        debugInfo.setChunkHitCount(0);
        debugInfo.setKeywordHitCount(0);
        debugInfo.setEntityHitCount(0);
        response.setDebug(debugInfo);
        return response;
    }

    private void normalizeFallbackResponse(AskResponse response) {
        response.setMode("BASELINE_FALLBACK");
        response.setRetrievalMode("BASELINE_FALLBACK");
        if (response.getKeywordHitCount() == null) {
            response.setKeywordHitCount(0);
        }
        if (response.getEntityHitCount() == null) {
            response.setEntityHitCount(0);
        }
        if (response.getMergedChunkCount() == null) {
            response.setMergedChunkCount(response.getChunks() == null ? 0 : response.getChunks().size());
        }
        if (response.getDebug() != null) {
            response.getDebug().setRetrievalType("BASELINE_FALLBACK");
        }
    }

    private void normalizeAgentGraphResponse(AskResponse response) {
        response.setMode("AGENT_GRAPH");
        response.setRetrievalMode("AGENT_GRAPH");
        if (response.getKeywordHitCount() == null) {
            response.setKeywordHitCount(0);
        }
        if (response.getEntityHitCount() == null) {
            response.setEntityHitCount(0);
        }
        if (response.getMergedChunkCount() == null) {
            response.setMergedChunkCount(0);
        }
        if (response.getDebug() != null) {
            response.getDebug().setRetrievalType("AGENT_GRAPH");
        }
    }

    private void normalizeBaselineResponse(AskResponse response) {
        response.setMode("BASELINE");
        response.setRetrievalMode("BASELINE");
        if (response.getKeywordHitCount() == null) {
            response.setKeywordHitCount(0);
        }
        if (response.getEntityHitCount() == null) {
            response.setEntityHitCount(0);
        }
        if (response.getMergedChunkCount() == null) {
            response.setMergedChunkCount(response.getChunks() == null ? 0 : response.getChunks().size());
        }
        if (response.getDebug() != null) {
            response.getDebug().setRetrievalType("BASELINE");
        }
    }
}
