package com.example.rag.service.impl;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvidenceSelectorImplTest {

    private final EvidenceSelectorImpl selector = new EvidenceSelectorImpl(new SentenceSplitterImpl());

    @Test
    void doesNotUseAnotherTablesFieldsAsEvidence() {
        List<String> evidence = selector.selectEvidenceSentences(
                "ORDERS\u8868\u6709\u54ea\u4e9b\u5b57\u6bb5\uff1f",
                "USERS\u8868\u5b57\u6bb5\u5305\u62ecUSER_ID\u3001NAME\u3001AGE\u3002"
        );

        assertTrue(evidence.isEmpty(), "Fields from USERS cannot support a question about ORDERS");
    }

    @Test
    void keepsEvidenceForTheRequestedTable() {
        List<String> evidence = selector.selectEvidenceSentences(
                "ORDERS\u8868\u6709\u54ea\u4e9b\u5b57\u6bb5\uff1f",
                "ORDERS\u8868\u5b57\u6bb5\u5305\u62ecORDER_ID\u3001AMOUNT\u3002"
        );

        assertEquals(1, evidence.size());
        assertTrue(evidence.get(0).contains("ORDER_ID"));
    }
}
