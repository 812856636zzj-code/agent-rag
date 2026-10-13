package com.example.rag.dto;

import java.util.ArrayList;
import java.util.List;

public class EvalRunSummary {

    private Long runId;
    private int total;
    private int baselineHitCount;
    private int graphHitCount;
    private int sourceHitCount;
    private int wrongAnswerCount;
    private int citationMatchedCount;
    private int citationMissingCount;
    private int abstainedCount;
    private double baselineHitRate;
    private double graphHitRate;
    private double sourceHitRate;
    private String resultsPath;
    private String reportPath;
    private List<EvalRunResult> results = new ArrayList<>();

    public Long getRunId() {
        return runId;
    }

    public void setRunId(Long runId) {
        this.runId = runId;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getBaselineHitCount() {
        return baselineHitCount;
    }

    public void setBaselineHitCount(int baselineHitCount) {
        this.baselineHitCount = baselineHitCount;
    }

    public int getGraphHitCount() {
        return graphHitCount;
    }

    public void setGraphHitCount(int graphHitCount) {
        this.graphHitCount = graphHitCount;
    }

    public int getSourceHitCount() {
        return sourceHitCount;
    }

    public void setSourceHitCount(int sourceHitCount) {
        this.sourceHitCount = sourceHitCount;
    }

    public double getBaselineHitRate() {
        return baselineHitRate;
    }

    public void setBaselineHitRate(double baselineHitRate) {
        this.baselineHitRate = baselineHitRate;
    }

    public double getGraphHitRate() {
        return graphHitRate;
    }

    public void setGraphHitRate(double graphHitRate) {
        this.graphHitRate = graphHitRate;
    }

    public double getSourceHitRate() {
        return sourceHitRate;
    }

    public void setSourceHitRate(double sourceHitRate) {
        this.sourceHitRate = sourceHitRate;
    }

    public String getResultsPath() {
        return resultsPath;
    }

    public void setResultsPath(String resultsPath) {
        this.resultsPath = resultsPath;
    }

    public String getReportPath() {
        return reportPath;
    }

    public void setReportPath(String reportPath) {
        this.reportPath = reportPath;
    }

    public List<EvalRunResult> getResults() {
        return results;
    }

    public void setResults(List<EvalRunResult> results) {
        this.results = results;
    }

    public int getWrongAnswerCount() {
        return wrongAnswerCount;
    }

    public void setWrongAnswerCount(int wrongAnswerCount) {
        this.wrongAnswerCount = wrongAnswerCount;
    }

    public int getCitationMatchedCount() {
        return citationMatchedCount;
    }

    public void setCitationMatchedCount(int citationMatchedCount) {
        this.citationMatchedCount = citationMatchedCount;
    }

    public int getCitationMissingCount() {
        return citationMissingCount;
    }

    public void setCitationMissingCount(int citationMissingCount) {
        this.citationMissingCount = citationMissingCount;
    }

    public int getAbstainedCount() {
        return abstainedCount;
    }

    public void setAbstainedCount(int abstainedCount) {
        this.abstainedCount = abstainedCount;
    }
}
