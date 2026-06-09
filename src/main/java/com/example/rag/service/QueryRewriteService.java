package com.example.rag.service;

import com.example.rag.dto.QueryRewriteResult;

public interface QueryRewriteService {

    QueryRewriteResult rewrite(String question);
}
