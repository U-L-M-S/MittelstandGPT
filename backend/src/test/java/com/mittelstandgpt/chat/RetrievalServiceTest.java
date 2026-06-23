package com.mittelstandgpt.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mittelstandgpt.document.DocumentIngestionService;
import com.mittelstandgpt.document.DocumentInfo;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

/** Unit tests for the provider-agnostic retrieval layer (mocked VectorStore). */
class RetrievalServiceTest {

    private final VectorStore vectorStore = mock(VectorStore.class);
    private final DocumentIngestionService documents = mock(DocumentIngestionService.class);

    private RetrievalService service(double threshold) {
        RagProperties props = new RagProperties();
        props.setSimilarityThreshold(threshold);
        return new RetrievalService(vectorStore, documents, props);
    }

    @Test
    void search_appliesQueryTopKAndThreshold() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(Document.builder().text("x").build()));
        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);

        service(0.5).search("urlaub", 3);

        verify(vectorStore).similaritySearch(captor.capture());
        assertThat(captor.getValue().getQuery()).isEqualTo("urlaub");
        assertThat(captor.getValue().getTopK()).isEqualTo(3);
        assertThat(captor.getValue().getSimilarityThreshold()).isEqualTo(0.5);
        assertThat(captor.getValue().getFilterExpression()).isNull();
    }

    @Test
    void search_blankQuery_returnsEmptyWithoutHittingStore() {
        assertThat(service(0.0).search("   ", 4)).isEmpty();
        verifyNoInteractions(vectorStore);
    }

    @Test
    void searchWithin_addsSourceFilterExpression() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);

        service(0.0).searchWithin("handbuch.pdf", "urlaub", 4);

        verify(vectorStore).similaritySearch(captor.capture());
        assertThat(captor.getValue().getFilterExpression())
                .as("a metadata filter on the source file is applied")
                .isNotNull();
    }

    @Test
    void documents_delegatesToRegistry() {
        when(documents.list())
                .thenReturn(List.of(new DocumentInfo("a.pdf", "application/pdf", 1L, 2, Instant.now())));

        assertThat(service(0.0).documents()).extracting(DocumentInfo::filename).containsExactly("a.pdf");
    }
}
