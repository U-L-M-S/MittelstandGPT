package com.mittelstandgpt.document;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Contract tests for the documents HTTP API (standalone MockMvc, mocked service). */
class DocumentControllerTest {

    private final DocumentIngestionService ingestion = mock(DocumentIngestionService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new DocumentController(ingestion)).build();
    }

    @Test
    void upload_returnsCreatedWithDocumentInfo() throws Exception {
        when(ingestion.ingest(any()))
                .thenReturn(new DocumentInfo("a.pdf", "application/pdf", 100L, 3, Instant.now()));
        MockMultipartFile file =
                new MockMultipartFile("file", "a.pdf", "application/pdf", "hello".getBytes());

        mockMvc.perform(multipart("/api/documents").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.filename").value("a.pdf"))
                .andExpect(jsonPath("$.chunks").value(3));
    }

    @Test
    void upload_emptyFile_returnsBadRequest() throws Exception {
        MockMultipartFile file =
                new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);

        mockMvc.perform(multipart("/api/documents").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void upload_unextractableDocument_returnsUnprocessableEntity() throws Exception {
        when(ingestion.ingest(any()))
                .thenThrow(new DocumentIngestionService.EmptyDocumentException("a.pdf"));
        MockMultipartFile file =
                new MockMultipartFile("file", "a.pdf", "application/pdf", "x".getBytes());

        mockMvc.perform(multipart("/api/documents").file(file))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void list_returnsDocuments() throws Exception {
        when(ingestion.list())
                .thenReturn(
                        List.of(new DocumentInfo("a.pdf", "application/pdf", 100L, 3, Instant.now())));

        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].filename").value("a.pdf"))
                .andExpect(jsonPath("$[0].chunks").value(3));
    }
}
