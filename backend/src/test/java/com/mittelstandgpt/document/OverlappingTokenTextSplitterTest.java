package com.mittelstandgpt.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

/** Unit tests for the overlap-aware token splitter and its metadata propagation. */
class OverlappingTokenTextSplitterTest {

    @Test
    void rejectsOverlapNotSmallerThanChunkSize() {
        assertThatThrownBy(() -> new OverlappingTokenTextSplitter(100, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shortTextYieldsSingleChunkAndKeepsMetadata() {
        var splitter = new OverlappingTokenTextSplitter(800, 150);

        List<Document> chunks =
                splitter.apply(
                        List.of(
                                Document.builder()
                                        .text("Ein kurzer Text.")
                                        .metadata(Map.of("source", "a.txt", "page", 1))
                                        .build()));

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getText()).contains("kurzer Text");
        assertThat(chunks.get(0).getMetadata())
                .containsEntry("source", "a.txt")
                .containsEntry("page", 1);
    }

    @Test
    void longTextSplitsIntoMultipleChunks() {
        var splitter = new OverlappingTokenTextSplitter(20, 5);
        String longText = "wort ".repeat(60); // well over 20 tokens

        List<Document> chunks =
                splitter.apply(
                        List.of(Document.builder().text(longText).metadata(Map.of("source", "a.txt")).build()));

        assertThat(chunks.size()).isGreaterThan(1);
        chunks.forEach(c -> assertThat(c.getMetadata()).containsEntry("source", "a.txt"));
    }
}
