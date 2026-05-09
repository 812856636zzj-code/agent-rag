package com.example.rag.service;

import java.util.List;

public interface EvidenceSelector {

    List<String> selectEvidenceSentences(String question, String text);
}
