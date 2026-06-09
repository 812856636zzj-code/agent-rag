package com.example.rag.dto;

import java.util.ArrayList;
import java.util.List;

public class EvalRunSummary {

    private int total;
    private int baselineHitCount;
    private int graphHitCount;
    private int sourceHitCount;
    private double baselineHitRate;
    private double graphHitRate;
    private double sourceHitRate;
    private String resultsPath;
    private String reportPath;
    private List<EvalRunResult> results = new ArrayList<>();

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
}
