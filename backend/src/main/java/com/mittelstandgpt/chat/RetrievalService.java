package com.mittelstandgpt.chat;

import com.mittelstandgpt.document.DocumentIngestionService;
import com.mittelstandgpt.document.DocumentInfo;
import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Thin, provider-agnostic retrieval layer over the Spring AI {@link VectorStore}.
 * Both the agentic loop ({@link AgenticRagService}) and the model-callable
 * {@link KnowledgeBaseTools} go through here, so retrieval behaves identically
 * whether it is driven by orchestration or by a tool call — and it works
 * unchanged on Qdrant (local) or Azure AI Search (azure).
 */
@Service
public class RetrievalService {

    private final VectorStore vectorStore;
    private final DocumentIngestionService documents;
    private final RagProperties props;

    public RetrievalService(
            VectorStore vectorStore, DocumentIngestionService documents, RagProperties props) {
        this.vectorStore = vectorStore;
        this.documents = documents;
        this.props = props;
    }

    /** Top-k similarity search across the whole knowledge base. */
    public List<Document> search(String query, int topK) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        List<Document> result =
                vectorStore.similaritySearch(
                        SearchRequest.builder()
                                .query(query)
                                .topK(Math.max(1, topK))
                                .similarityThreshold(props.getSimilarityThreshold())
                                .build());
        return result != null ? result : List.of();
    }

    /** Similarity search constrained to a single source file. */
    public List<Document> searchWithin(String filename, String query, int topK) {
        if (!StringUtils.hasText(query) || !StringUtils.hasText(filename)) {
            return List.of();
        }
        // Quotes are stripped to keep the metadata filter expression well-formed.
        String safe = filename.replace("'", "");
        List<Document> result =
                vectorStore.similaritySearch(
                        SearchRequest.builder()
                                .query(query)
                                .topK(Math.max(1, topK))
                                .similarityThreshold(props.getSimilarityThreshold())
                                .filterExpression(
                                        DocumentIngestionService.META_SOURCE + " == '" + safe + "'")
                                .build());
        return result != null ? result : List.of();
    }

    /** The documents currently in the knowledge base (from the in-memory registry). */
    public List<DocumentInfo> documents() {
        return documents.list();
    }
}
