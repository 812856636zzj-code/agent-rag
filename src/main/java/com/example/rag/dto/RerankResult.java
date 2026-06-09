package com.example.rag.dto;

import java.util.ArrayList;
import java.util.List;

public class RerankResult {

    private QueryRewriteResult rewrite;
    private List<ChunkHit> rerankedChunks = new ArrayList<>();
    private List<RetrievalScoreDetail> topScoreDetails = new ArrayList<>();
    private List<Long> beforeChunkIds = new ArrayList<>();
    private List<Long> afterChunkIds = new ArrayList<>();
    private int uniqueCandidateCount;
    private double candidateScoreSpread;
    private double top1Score;
    private double top2Score;
    private double topScoreGap;
    private boolean rerankChanged;
    private String rerankChangeReason;

    public QueryRewriteResult getRewrite() {
        return rewrite;
    }

    public void setRewrite(QueryRewriteResult rewrite) {
        this.rewrite = rewrite;
    }

    public List<ChunkHit> getRerankedChunks() {
        return rerankedChunks;
    }

    public void setRerankedChunks(List<ChunkHit> rerankedChunks) {
        this.rerankedChunks = rerankedChunks;
    }

    public List<RetrievalScoreDetail> getTopScoreDetails() {
        return topScoreDetails;
    }

    public void setTopScoreDetails(List<RetrievalScoreDetail> topScoreDetails) {
        this.topScoreDetails = topScoreDetails;
    }

    public List<Long> getBeforeChunkIds() {
        return beforeChunkIds;
    }

    public void setBeforeChunkIds(List<Long> beforeChunkIds) {
        this.beforeChunkIds = beforeChunkIds;
    }

    public List<Long> getAfterChunkIds() {
        return afterChunkIds;
    }

    public void setAfterChunkIds(List<Long> afterChunkIds) {
        this.afterChunkIds = afterChunkIds;
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
}
