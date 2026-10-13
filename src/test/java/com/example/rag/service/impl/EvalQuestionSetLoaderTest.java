package com.example.rag.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EvalQuestionSetLoaderTest {

    @TempDir
    Path tempDir;

    private final EvalQuestionSetLoader loader = new EvalQuestionSetLoader(new ObjectMapper());

    @Test
    void hashesTheExactQuestionSetBytesAndPreservesCaseOrder() throws IOException {
        Path questions = tempDir.resolve("questions.json");
        String json = "[{\"id\":\"A\",\"question\":\"alpha?\",\"expectedKeywords\":[\"alpha\"]},"
                + "{\"id\":\"B\",\"question\":\"beta?\",\"expectedKeywords\":[\"beta\"]}]";
        Files.writeString(questions, json, StandardCharsets.UTF_8);
        EvalQuestionSet first = loader.load(questions);
        EvalQuestionSet again = loader.load(questions);

        assertEquals(2, first.questions().size());
        assertEquals("A", first.questions().get(0).getId());
        assertEquals("B", first.questions().get(1).getId());
        assertEquals(64, first.sha256().length());
        assertEquals(first.sha256(), again.sha256());

        Files.writeString(questions, json + "\n", StandardCharsets.UTF_8);
        assertNotEquals(first.sha256(), loader.load(questions).sha256());
    }

    @Test
    void refusesDuplicateIdsAndEmptyExpectedKeywordsBeforeStartingARun() throws IOException {
        Path questions = tempDir.resolve("invalid.json");
        Files.writeString(questions,
                "[{\"id\":\"A\",\"question\":\"one?\",\"expectedKeywords\":[\"one\"]},"
                        + "{\"id\":\"A\",\"question\":\"two?\",\"expectedKeywords\":[\"two\"]}]",
                StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> loader.load(questions));

        Files.writeString(questions,
                "[{\"id\":\"A\",\"question\":\"one?\",\"expectedKeywords\":[]}]",
                StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> loader.load(questions));
    }
}
