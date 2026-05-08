package com.example.rag.repository;

import java.util.List;

public interface DocumentEntityLinkRepositoryCustom {

    List<Object[]> searchChunksByEntityNormalizedNames(List<String> normalizedNames);
}
