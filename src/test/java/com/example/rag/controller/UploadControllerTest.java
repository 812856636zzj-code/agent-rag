package com.example.rag.controller;

import com.example.rag.entity.Document;
import com.example.rag.service.DocumentChunkService;
import com.example.rag.service.DocumentMarkdownConverter;
import com.example.rag.service.DocumentService;
import com.example.rag.service.SearchService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class UploadControllerTest {

    @Test
    void rejectsFilenameWithDirectoryComponentsBeforeReadingOrSaving() throws Exception {
        for (String filename : new String[]{"../outside.txt", "..\\outside.txt", "C:\\fakepath\\outside.txt"}) {
            DocumentService documentService = mock(DocumentService.class);
            DocumentChunkService chunkService = mock(DocumentChunkService.class);
            DocumentMarkdownConverter converter = new DocumentMarkdownConverter();
            SearchService searchService = mock(SearchService.class);

            Document existing = new Document();
            existing.setId(7L);
            existing.setFileName("outside.txt");
            when(documentService.findDuplicateDocument(anyString(), anyString()))
                    .thenReturn(Optional.of(existing));

            UploadController controller = new UploadController(
                    documentService, chunkService, converter, searchService);
            MockMvc mvc = standaloneSetup(controller).build();
            MockMultipartFile file = new MockMultipartFile(
                    "file", filename, "text/plain", "content".getBytes(StandardCharsets.UTF_8));

            mvc.perform(multipart("/upload").file(file))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.uploaded").value(false));
            verifyNoInteractions(documentService, chunkService, searchService);
        }
    }
}
