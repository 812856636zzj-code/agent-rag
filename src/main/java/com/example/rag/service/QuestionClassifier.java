package com.example.rag.service;

import com.example.rag.enums.QuestionType;

public interface QuestionClassifier {

    QuestionType classify(String question);
}
