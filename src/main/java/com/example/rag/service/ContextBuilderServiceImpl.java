package com.example.rag.service;

import com.example.rag.dto.AskContext;
import com.example.rag.dto.ChunkHit;
import com.example.rag.dto.ChunkResponse;
import com.example.rag.dto.SearchResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ContextBuilderServiceImpl implements ContextBuilderService {

    private static final int TOP_CHUNK_LIMIT = 5;

    @Override
    public AskContext buildContext(String question, String keyword, SearchResult searchResult) {
        AskContext context = new AskContext();
        context.setQuestion(question);
        context.setKeyword(keyword);
        context.setSourceDocIds(new ArrayList<>(searchResult.getMatchedDocumentIds()));

        List<ChunkResponse> topChunks = new ArrayList<>();
        int max = Math.min(TOP_CHUNK_LIMIT, searchResult.getMatchedChunks().size());
        for (int i = 0; i < max; i++) {
            ChunkHit hit = searchResult.getMatchedChunks().get(i);
            ChunkResponse chunk = new ChunkResponse();
            chunk.setDocumentId(hit.getDocumentId());
            chunk.setChunkIndex(hit.getChunkIndex());
            chunk.setContent(hit.getContent());
            chunk.setHighlightContent(hit.getHighlightContent());
            topChunks.add(chunk);
        }
        context.setTopChunks(topChunks);
        context.setStructuredContext(buildStructuredContext(
                question,
                keyword,
                topChunks,
                searchResult.getMatchedDocumentIds(),
                searchResult.getSearchMeta() == null ? topChunks.size() : searchResult.getSearchMeta().getTotalHits()
        ));
        return context;
    }

    private String buildStructuredContext(String question,
                                          String keyword,
                                          List<ChunkResponse> topChunks,
                                          List<Long> sourceDocIds,
                                          Integer totalHits) {
        StringBuilder sb = new StringBuilder();
        sb.append("问题: ").append(question).append("\n");
        sb.append("关键词: ").append(keyword).append("\n");
        sb.append("真实命中块数: ").append(totalHits == null ? 0 : totalHits).append("\n");
        sb.append("展示块数: ").append(topChunks.size()).append("\n");
        sb.append("来源文档ID: ").append(sourceDocIds).append("\n");
        sb.append("命中摘要:").append("\n");

        for (int i = 0; i < topChunks.size(); i++) {
            ChunkResponse chunk = topChunks.get(i);
            String snippet = chunk.getHighlightContent();
            if (snippet == null || snippet.isEmpty()) {
                snippet = chunk.getContent();
            }
            sb.append(i + 1)
                    .append(". [doc=")
                    .append(chunk.getDocumentId())
                    .append(", chunk=")
                    .append(chunk.getChunkIndex())
                    .append("] ")
                    .append(snippet)
                    .append("\n");
        }

        return sb.toString().trim();
    }
}
