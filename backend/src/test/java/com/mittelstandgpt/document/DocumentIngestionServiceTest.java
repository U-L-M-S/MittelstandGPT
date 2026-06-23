package com.mittelstandgpt.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Unit tests for the ingestion pipeline (real Tika + splitter, mocked VectorStore).
 * An empty Qdrant REST URL keeps it provider-agnostic and skips the registry rebuild.
 */
class DocumentIngestionServiceTest {

    private final VectorStore vectorStore = mock(VectorStore.class);
    private final DocumentIngestionService service =
            new DocumentIngestionService(vectorStore, "", "mittelstandgpt");

    @Test
    void ingest_parsesChunksEmbedsAndRegisters() throws Exception {
        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "notiz.txt",
                        "text/plain",
                        "Dies ist ein Testdokument mit etwas Inhalt zum Einlesen."
                                .getBytes(StandardCharsets.UTF_8));

        DocumentInfo info = service.ingest(file);

        assertThat(info.filename()).isEqualTo("notiz.txt");
        assertThat(info.chunks()).isGreaterThan(0);
        verify(vectorStore).add(anyList());
        assertThat(service.list()).extracting(DocumentInfo::filename).contains("notiz.txt");
    }

    @Test
    void ingest_blankDocument_throwsAndStoresNothing() {
        MockMultipartFile file =
                new MockMultipartFile("file", "leer.txt", "text/plain", "   \n  ".getBytes());

        assertThatThrownBy(() -> service.ingest(file))
                .isInstanceOf(DocumentIngestionService.EmptyDocumentException.class);
        verifyNoInteractions(vectorStore);
    }
}
