package com.example.rag.service;

import com.example.rag.dto.QueryUnderstanding;

public interface QueryUnderstandingService {

    QueryUnderstanding understand(String question);
}
