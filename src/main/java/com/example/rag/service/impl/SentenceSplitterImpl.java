package com.example.rag.service.impl;

import com.example.rag.service.SentenceSplitter;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class SentenceSplitterImpl implements SentenceSplitter {

    @Override
    public List<String> split(String text) {
        List<String> sentences = new ArrayList<>();
        if (!StringUtils.hasText(text)) {
            return sentences;
        }

        String normalized = text.replace("\r", "\n");
        String[] parts = normalized.split("[\\u3002\\uFF01\\uFF1F\\uFF1B\\n]|(?<=[A-Za-z0-9_])([.!?;])(?=\\s|$)");
        for (String part : parts) {
            String sentence = part == null ? "" : part.replaceAll("\\s+", " ").trim();
            if (StringUtils.hasText(sentence)) {
                sentences.add(sentence);
            }
        }
        return sentences;
    }
}
