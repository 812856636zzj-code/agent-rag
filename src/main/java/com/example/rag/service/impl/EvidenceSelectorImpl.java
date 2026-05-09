package com.example.rag.service.impl;

import com.example.rag.service.EvidenceSelector;
import com.example.rag.service.SentenceSplitter;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EvidenceSelectorImpl implements EvidenceSelector {

    private static final Pattern UPPER_ENTITY_PATTERN = Pattern.compile("\\b[A-Z][A-Z0-9_]{2,}\\b");
    private static final String TABLE_FIELDS_1 = "\u5b57\u6bb5\u5305\u62ec";
    private static final String TABLE_FIELDS_2 = "\u5e38\u89c1\u5b57\u6bb5\u5305\u62ec";
    private static final String TABLE_FIELDS_3 = "\u8868\u5b57\u6bb5\u5305\u62ec";
    private static final String SORT_RULE = "\u6392\u5e8f\u89c4\u5219";
    private static final String QUESTION_FIELDS_1 = "\u8868\u6709\u54ea\u4e9b\u5b57\u6bb5";
    private static final String QUESTION_FIELDS_2 = "\u6709\u54ea\u4e9b\u5b57\u6bb5";
    private static final String QUESTION_FIELDS_3 = "\u5b57\u6bb5\u5305\u62ec\u4ec0\u4e48";

    private final SentenceSplitter sentenceSplitter;

    public EvidenceSelectorImpl(SentenceSplitter sentenceSplitter) {
        this.sentenceSplitter = sentenceSplitter;
    }

    @Override
    public List<String> selectEvidenceSentences(String question, String text) {
        List<String> sentences = sentenceSplitter.split(text);
        if (sentences.isEmpty()) {
            return new ArrayList<>();
        }

        LinkedHashSet<String> selected = new LinkedHashSet<>();
        boolean tableFieldQuestion = isTableFieldQuestion(question);
        Set<String> queryTokens = extractQueryTokens(question);

        for (String sentence : sentences) {
            if (tableFieldQuestion && shouldSkipSortSentence(sentence)) {
                continue;
            }
            int score = scoreSentence(question, sentence, queryTokens, tableFieldQuestion);
            if (score > 0) {
                selected.add(sentence);
            }
        }

        if (selected.isEmpty()) {
            for (String sentence : sentences) {
                if (tableFieldQuestion && shouldSkipSortSentence(sentence)) {
                    continue;
                }
                selected.add(sentence);
                break;
            }
        }

        return new ArrayList<>(selected);
    }

    private int scoreSentence(String question, String sentence, Set<String> queryTokens, boolean tableFieldQuestion) {
        if (!StringUtils.hasText(sentence)) {
            return 0;
        }
        String lowerSentence = sentence.toLowerCase(Locale.ROOT);
        int score = 0;

        for (String token : queryTokens) {
            if (lowerSentence.contains(token.toLowerCase(Locale.ROOT))) {
                score += 2;
            }
        }

        if (tableFieldQuestion) {
            if (containsAny(sentence, TABLE_FIELDS_1, TABLE_FIELDS_2, TABLE_FIELDS_3)) {
                score += 10;
            }
            if (containsAny(lowerSentence, "create table", "column_name", "user_tab_columns")) {
                score += 8;
            }
            if (containsUpperFieldList(sentence)) {
                score += 6;
            }
            if (shouldSkipSortSentence(sentence)) {
                score -= 20;
            }
        }

        if (containsAny(question, SORT_RULE) && containsAny(lowerSentence, "order by", "asc", "desc")) {
            score += 6;
        }

        return score;
    }

    private boolean isTableFieldQuestion(String question) {
        return containsAny(question, QUESTION_FIELDS_1, QUESTION_FIELDS_2, QUESTION_FIELDS_3);
    }

    private boolean shouldSkipSortSentence(String sentence) {
        String lower = sentence == null ? "" : sentence.toLowerCase(Locale.ROOT);
        return lower.contains("order by") || lower.contains(" asc") || lower.contains(" desc");
    }

    private boolean containsUpperFieldList(String sentence) {
        Matcher matcher = UPPER_ENTITY_PATTERN.matcher(sentence == null ? "" : sentence);
        int count = 0;
        while (matcher.find()) {
            count++;
            if (count >= 3) {
                return true;
            }
        }
        return false;
    }

    private Set<String> extractQueryTokens(String question) {
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        if (!StringUtils.hasText(question)) {
            return tokens;
        }

        Matcher matcher = UPPER_ENTITY_PATTERN.matcher(question);
        while (matcher.find()) {
            tokens.add(matcher.group());
        }

        String[] parts = question.split("[^A-Za-z0-9_/]+");
        for (String part : parts) {
            if (StringUtils.hasText(part) && part.length() >= 3) {
                tokens.add(part);
            }
        }
        return tokens;
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
