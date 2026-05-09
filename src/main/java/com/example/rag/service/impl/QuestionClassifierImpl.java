package com.example.rag.service.impl;

import com.example.rag.enums.QuestionType;
import com.example.rag.service.QuestionClassifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Service
public class QuestionClassifierImpl implements QuestionClassifier {

    @Override
    public QuestionType classify(String question) {
        if (!StringUtils.hasText(question)) {
            return QuestionType.UNKNOWN;
        }

        String lower = question.trim().toLowerCase(Locale.ROOT);

        if (containsAny(lower,
                "\u5173\u8054\u54ea\u4e9b\u8868",
                "\u54ea\u4e9b\u5b57\u6bb5",
                "\u8c03\u7528",
                "\u4f9d\u8d56",
                "\u5c5e\u4e8e",
                "\u7528\u5230",
                "\u5f71\u54cd\u54ea\u4e9b",
                "\u5bf9\u5e94\u54ea\u4e9b",
                "\u6709\u54ea\u4e9b\u65b9\u6cd5",
                "\u6709\u54ea\u4e9b\u7c7b",
                "\u548c\u54ea\u4e9b\u670d\u52a1\u6709\u5173",
                "\u54ea\u4e9b\u670d\u52a1\u6709\u5173",
                "\u6709\u5173",
                "\u76f8\u5173\u670d\u52a1",
                "\u5173\u8054\u670d\u52a1",
                "\u548c\u54ea\u4e9b\u7c7b\u6709\u5173",
                "\u548c\u54ea\u4e9b\u6a21\u5757\u6709\u5173")) {
            return QuestionType.RELATION;
        }

        if (containsAny(lower,
                "\u662f\u4ec0\u4e48",
                "\u4f5c\u7528",
                "\u542b\u4e49",
                "\u5b9a\u4e49")) {
            return QuestionType.DEFINITION;
        }

        if (containsAny(lower,
                "\u600e\u4e48",
                "\u5982\u4f55",
                "\u6d41\u7a0b",
                "\u6b65\u9aa4",
                "\u505a\u4e86\u4ec0\u4e48")) {
            return QuestionType.PROCEDURE;
        }

        return QuestionType.GENERAL;
    }

    private boolean containsAny(String text, String... terms) {
        for (String term : terms) {
            if (text.contains(term.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
