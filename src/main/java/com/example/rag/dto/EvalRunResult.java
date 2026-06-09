package com.example.rag.dto;

import java.util.ArrayList;
import java.util.List;

public class EvalRunResult {

    private String id;
    private String originalQuestion;
    private String question;
    private String type;
    private List<String> expectedKeywords = new ArrayList<>();
    private List<String> expectedSource = new ArrayList<>();
    private List<String> missingExpectedKeywords = new ArrayList<>();
    private String baselineAnswer;
    private String graphAnswer;
    private boolean baselineHit;
    private boolean graphHit;
    private boolean sourceHit;
    private boolean sourceMatched;
    private boolean baselineNoise;
    private boolean graphNoise;
    private int baselineReadabilityScore;
    private int graphReadabilityScore;
    private String rewrittenQuery;
    private List<String> expandedKeywords = new ArrayList<>();
    private List<String> searchQueries = new ArrayList<>();
    private List<String> matchedQueries = new ArrayList<>();
    private boolean recallFallbackUsed;
    private java.util.Map<String, Integer> candidateCountByQuery = new java.util.LinkedHashMap<>();
    private int candidateCountAfterMerge;
    private int duplicateCandidateRemovedCount;
    private int uniqueCandidateCount;
    private double candidateScoreSpread;
    private double top1Score;
    private double top2Score;
    private double topScoreGap;
    private boolean rerankChanged;
    private String rerankChangeReason;
    private String detectedIntent;
    private List<String> detectedEntities = new ArrayList<>();
    private String graphRewrittenQuery;
    private List<String> graphExpandedKeywords = new ArrayList<>();
    private String graphDetectedIntent;
    private List<String> graphDetectedEntities = new ArrayList<>();
    private int candidateCountBeforeRerank;
    private int candidateCountAfterRerank;
    private List<Long> topChunksBeforeRerank = new ArrayList<>();
    private List<Long> topChunksAfterRerank = new ArrayList<>();
    private List<RetrievalScoreDetail> topScoreDetails = new ArrayList<>();
    private List<Long> rerankBeforeChunkIds = new ArrayList<>();
    private List<Long> rerankAfterChunkIds = new ArrayList<>();
    private List<String> diagnosisReason = new ArrayList<>();
    private String notes;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOriginalQuestion() {
        return originalQuestion;
    }

    public void setOriginalQuestion(String originalQuestion) {
        this.originalQuestion = originalQuestion;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<String> getExpectedKeywords() {
        return expectedKeywords;
    }

    public void setExpectedKeywords(List<String> expectedKeywords) {
        this.expectedKeywords = expectedKeywords;
    }

    public List<String> getExpectedSource() {
        return expectedSource;
    }

    public void setExpectedSource(List<String> expectedSource) {
        this.expectedSource = expectedSource;
    }

    public List<String> getMissingExpectedKeywords() {
        return missingExpectedKeywords;
    }

    public void setMissingExpectedKeywords(List<String> missingExpectedKeywords) {
        this.missingExpectedKeywords = missingExpectedKeywords;
    }

    public String getBaselineAnswer() {
        return baselineAnswer;
    }

    public void setBaselineAnswer(String baselineAnswer) {
        this.baselineAnswer = baselineAnswer;
    }

    public String getGraphAnswer() {
        return graphAnswer;
    }

    public void setGraphAnswer(String graphAnswer) {
        this.graphAnswer = graphAnswer;
    }

    public boolean isBaselineHit() {
        return baselineHit;
    }

    public void setBaselineHit(boolean baselineHit) {
        this.baselineHit = baselineHit;
    }

    public boolean isGraphHit() {
        return graphHit;
    }

    public void setGraphHit(boolean graphHit) {
        this.graphHit = graphHit;
    }

    public boolean isSourceHit() {
        return sourceHit;
    }

    public void setSourceHit(boolean sourceHit) {
        this.sourceHit = sourceHit;
    }

    public boolean isSourceMatched() {
        return sourceMatched;
    }

    public void setSourceMatched(boolean sourceMatched) {
        this.sourceMatched = sourceMatched;
    }

    public boolean isBaselineNoise() {
        return baselineNoise;
    }

    public void setBaselineNoise(boolean baselineNoise) {
        this.baselineNoise = baselineNoise;
    }

    public boolean isGraphNoise() {
        return graphNoise;
    }

    public void setGraphNoise(boolean graphNoise) {
        this.graphNoise = graphNoise;
    }

    public int getBaselineReadabilityScore() {
        return baselineReadabilityScore;
    }

    public void setBaselineReadabilityScore(int baselineReadabilityScore) {
        this.baselineReadabilityScore = baselineReadabilityScore;
    }

    public int getGraphReadabilityScore() {
        return graphReadabilityScore;
    }

    public void setGraphReadabilityScore(int graphReadabilityScore) {
        this.graphReadabilityScore = graphReadabilityScore;
    }

    public String getRewrittenQuery() {
        return rewrittenQuery;
    }

    public void setRewrittenQuery(String rewrittenQuery) {
        this.rewrittenQuery = rewrittenQuery;
    }

    public List<String> getExpandedKeywords() {
        return expandedKeywords;
    }

    public void setExpandedKeywords(List<String> expandedKeywords) {
        this.expandedKeywords = expandedKeywords;
    }

    public List<String> getSearchQueries() {
        return searchQueries;
    }

    public void setSearchQueries(List<String> searchQueries) {
        this.searchQueries = searchQueries;
    }

    public List<String> getMatchedQueries() {
        return matchedQueries;
    }

    public void setMatchedQueries(List<String> matchedQueries) {
        this.matchedQueries = matchedQueries;
    }

    public boolean isRecallFallbackUsed() {
        return recallFallbackUsed;
    }

    public void setRecallFallbackUsed(boolean recallFallbackUsed) {
        this.recallFallbackUsed = recallFallbackUsed;
    }

    public java.util.Map<String, Integer> getCandidateCountByQuery() {
        return candidateCountByQuery;
    }

    public void setCandidateCountByQuery(java.util.Map<String, Integer> candidateCountByQuery) {
        this.candidateCountByQuery = candidateCountByQuery;
    }

    public int getCandidateCountAfterMerge() {
        return candidateCountAfterMerge;
    }

    public void setCandidateCountAfterMerge(int candidateCountAfterMerge) {
        this.candidateCountAfterMerge = candidateCountAfterMerge;
    }

    public int getDuplicateCandidateRemovedCount() {
        return duplicateCandidateRemovedCount;
    }

    public void setDuplicateCandidateRemovedCount(int duplicateCandidateRemovedCount) {
        this.duplicateCandidateRemovedCount = duplicateCandidateRemovedCount;
    }

    public int getUniqueCandidateCount() {
        return uniqueCandidateCount;
    }

    public void setUniqueCandidateCount(int uniqueCandidateCount) {
        this.uniqueCandidateCount = uniqueCandidateCount;
    }

    public double getCandidateScoreSpread() {
        return candidateScoreSpread;
    }

    public void setCandidateScoreSpread(double candidateScoreSpread) {
        this.candidateScoreSpread = candidateScoreSpread;
    }

    public double getTop1Score() {
        return top1Score;
    }

    public void setTop1Score(double top1Score) {
        this.top1Score = top1Score;
    }

    public double getTop2Score() {
        return top2Score;
    }

    public void setTop2Score(double top2Score) {
        this.top2Score = top2Score;
    }

    public double getTopScoreGap() {
        return topScoreGap;
    }

    public void setTopScoreGap(double topScoreGap) {
        this.topScoreGap = topScoreGap;
    }

    public boolean isRerankChanged() {
        return rerankChanged;
    }

    public void setRerankChanged(boolean rerankChanged) {
        this.rerankChanged = rerankChanged;
    }

    public String getRerankChangeReason() {
        return rerankChangeReason;
    }

    public void setRerankChangeReason(String rerankChangeReason) {
        this.rerankChangeReason = rerankChangeReason;
    }

    public String getDetectedIntent() {
        return detectedIntent;
    }

    public void setDetectedIntent(String detectedIntent) {
        this.detectedIntent = detectedIntent;
    }

    public List<String> getDetectedEntities() {
        return detectedEntities;
    }

    public void setDetectedEntities(List<String> detectedEntities) {
        this.detectedEntities = detectedEntities;
    }

    public String getGraphRewrittenQuery() {
        return graphRewrittenQuery;
    }

    public void setGraphRewrittenQuery(String graphRewrittenQuery) {
        this.graphRewrittenQuery = graphRewrittenQuery;
    }

    public List<String> getGraphExpandedKeywords() {
        return graphExpandedKeywords;
    }

    public void setGraphExpandedKeywords(List<String> graphExpandedKeywords) {
        this.graphExpandedKeywords = graphExpandedKeywords;
    }

    public String getGraphDetectedIntent() {
        return graphDetectedIntent;
    }

    public void setGraphDetectedIntent(String graphDetectedIntent) {
        this.graphDetectedIntent = graphDetectedIntent;
    }

    public List<String> getGraphDetectedEntities() {
        return graphDetectedEntities;
    }

    public void setGraphDetectedEntities(List<String> graphDetectedEntities) {
        this.graphDetectedEntities = graphDetectedEntities;
    }

    public int getCandidateCountBeforeRerank() {
        return candidateCountBeforeRerank;
    }

    public void setCandidateCountBeforeRerank(int candidateCountBeforeRerank) {
        this.candidateCountBeforeRerank = candidateCountBeforeRerank;
    }

    public int getCandidateCountAfterRerank() {
        return candidateCountAfterRerank;
    }

    public void setCandidateCountAfterRerank(int candidateCountAfterRerank) {
        this.candidateCountAfterRerank = candidateCountAfterRerank;
    }

    public List<Long> getTopChunksBeforeRerank() {
        return topChunksBeforeRerank;
    }

    public void setTopChunksBeforeRerank(List<Long> topChunksBeforeRerank) {
        this.topChunksBeforeRerank = topChunksBeforeRerank;
    }

    public List<Long> getTopChunksAfterRerank() {
        return topChunksAfterRerank;
    }

    public void setTopChunksAfterRerank(List<Long> topChunksAfterRerank) {
        this.topChunksAfterRerank = topChunksAfterRerank;
    }

    public List<RetrievalScoreDetail> getTopScoreDetails() {
        return topScoreDetails;
    }

    public void setTopScoreDetails(List<RetrievalScoreDetail> topScoreDetails) {
        this.topScoreDetails = topScoreDetails;
    }

    public List<Long> getRerankBeforeChunkIds() {
        return rerankBeforeChunkIds;
    }

    public void setRerankBeforeChunkIds(List<Long> rerankBeforeChunkIds) {
        this.rerankBeforeChunkIds = rerankBeforeChunkIds;
    }

    public List<Long> getRerankAfterChunkIds() {
        return rerankAfterChunkIds;
    }

    public void setRerankAfterChunkIds(List<Long> rerankAfterChunkIds) {
        this.rerankAfterChunkIds = rerankAfterChunkIds;
    }

    public List<String> getDiagnosisReason() {
        return diagnosisReason;
    }

    public void setDiagnosisReason(List<String> diagnosisReason) {
        this.diagnosisReason = diagnosisReason;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
