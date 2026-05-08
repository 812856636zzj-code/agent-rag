package com.example.rag.service.impl;

import com.example.rag.dto.AskContext;
import com.example.rag.service.AnswerBuilderService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AnswerBuilderServiceImpl implements AnswerBuilderService {

    @Override
    public String buildAnswer(AskContext context) {
        if (context == null) {
            return "\u6682\u672a\u751f\u6210\u6709\u6548\u56de\u7b54\u3002";
        }

        if (context.getTopChunks() == null || context.getTopChunks().isEmpty()) {
            return "\u672a\u68c0\u7d22\u5230\u8db3\u591f\u76f8\u5173\u7684\u8d44\u6599\uff0c\u6682\u65f6\u65e0\u6cd5\u57fa\u4e8e\u77e5\u8bc6\u5e93\u56de\u7b54\u8be5\u95ee\u9898\u3002";
        }

        List<String> answerHints = normalizeHints(context.getAnswerHints());
        if (answerHints.isEmpty()) {
            return "\u672a\u68c0\u7d22\u5230\u8db3\u591f\u76f8\u5173\u7684\u8d44\u6599\uff0c\u6682\u65f6\u65e0\u6cd5\u57fa\u4e8e\u77e5\u8bc6\u5e93\u56de\u7b54\u8be5\u95ee\u9898\u3002";
        }

        StringBuilder answer = new StringBuilder();
        answer.append("\u6839\u636e\u68c0\u7d22\u5230\u7684\u8d44\u6599\uff0c\u53ef\u4ee5\u8fd9\u6837\u56de\u7b54\uff1a").append("\n\n");
        answer.append(buildNaturalLanguageAnswer(answerHints)).append("\n\n");
        answer.append("\u53c2\u8003\u4f9d\u636e\uff1a");

        for (int i = 0; i < answerHints.size(); i++) {
            answer.append("\n")
                    .append(i + 1)
                    .append(". ")
                    .append(answerHints.get(i));
        }

        return answer.toString();
    }

    private List<String> normalizeHints(List<String> rawHints) {
        List<String> normalizedHints = new ArrayList<>();
        if (rawHints == null || rawHints.isEmpty()) {
            return normalizedHints;
        }

        for (String hint : rawHints) {
            if (hint == null) {
                continue;
            }
            String normalized = hint.replaceAll("\\s+", " ").trim();
            if (!normalized.isEmpty()) {
                normalizedHints.add(normalized);
            }
        }
        return normalizedHints;
    }

    private String buildNaturalLanguageAnswer(List<String> answerHints) {
        if (answerHints.isEmpty()) {
            return "\u6682\u672a\u751f\u6210\u6709\u6548\u56de\u7b54\u3002";
        }

        StringBuilder summary = new StringBuilder();
        summary.append(answerHints.get(0));

        for (int i = 1; i < answerHints.size(); i++) {
            summary.append("\uff1b");
            summary.append(answerHints.get(i));
        }

        String result = summary.toString().trim();
        if (!result.endsWith("\u3002") && !result.endsWith("\uff01") && !result.endsWith("\uff1f")) {
            result = result + "\u3002";
        }
        return result;
    }
}
