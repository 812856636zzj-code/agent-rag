package com.example.rag.dto;

import java.util.List;

/** Descending run IDs; nextBefore is absent when there is no next page. */
public record EvalRunPage(List<EvalRunView> items, Long nextBefore) {
}
