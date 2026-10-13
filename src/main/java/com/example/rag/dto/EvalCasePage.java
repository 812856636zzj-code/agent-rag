package com.example.rag.dto;

import java.util.List;

/** Ascending case positions; nextAfter is absent on the last page. */
public record EvalCasePage(List<EvalCaseView> items, Integer nextAfter) {
}
