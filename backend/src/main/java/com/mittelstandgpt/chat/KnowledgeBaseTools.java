package com.mittelstandgpt.chat;

import com.mittelstandgpt.document.DocumentIngestionService;
import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * The knowledge base exposed as Spring AI tools. These wrap the same
 * {@link RetrievalService} primitives the agentic loop uses, so a model can call
 * them directly (tool calling) and they can be re-exposed over MCP (roadmap)
 * without diverging from production retrieval behaviour.
 */
@Component
public class KnowledgeBaseTools {

    private static final int DEFAULT_TOP_K = 4;

    private final RetrievalService retrieval;

    public KnowledgeBaseTools(RetrievalService retrieval) {
        this.retrieval = retrieval;
    }

    @Tool(
            description =
                    "Durchsuche die Wissensbasis der hochgeladenen Unternehmensdokumente "
                            + "nach Passagen, die zu einer Suchanfrage passen. Liefert "
                            + "Textauszüge mit Quelldatei und Seitenzahl.")
    public List<RetrievedChunk> searchKnowledgeBase(
            @ToolParam(description = "die Suchanfrage in natürlicher Sprache") String query,
            @ToolParam(description = "Anzahl der gewünschten Auszüge (Standard 4)", required = false)
                    Integer topK) {
        int k = topK != null && topK > 0 ? topK : DEFAULT_TOP_K;
        return toChunks(retrieval.search(query, k));
    }

    @Tool(description = "Liste die aktuell in der Wissensbasis verfügbaren Dokumente auf.")
    public List<DocumentSummary> listDocuments() {
        return retrieval.documents().stream()
                .map(d -> new DocumentSummary(d.filename(), d.chunks()))
                .toList();
    }

    @Tool(
            description =
                    "Durchsuche ein einzelnes, namentlich genanntes Dokument nach Passagen "
                            + "zu einer Suchanfrage.")
    public List<RetrievedChunk> searchWithinDocument(
            @ToolParam(description = "Dateiname des Dokuments, z. B. handbuch.pdf") String filename,
            @ToolParam(description = "die Suchanfrage in natürlicher Sprache") String query) {
        return toChunks(retrieval.searchWithin(filename, query, DEFAULT_TOP_K));
    }

    private static List<RetrievedChunk> toChunks(List<Document> docs) {
        return docs.stream().map(KnowledgeBaseTools::toChunk).toList();
    }

    private static RetrievedChunk toChunk(Document doc) {
        Object source = doc.getMetadata().get(DocumentIngestionService.META_SOURCE);
        Object page = doc.getMetadata().get(DocumentIngestionService.META_PAGE);
        return new RetrievedChunk(
                doc.getText(),
                source != null ? source.toString() : null,
                page instanceof Number n ? n.intValue() : null);
    }

    /** A retrieved passage as handed to the model. */
    public record RetrievedChunk(String text, String source, Integer page) {}

    /** A document listing entry as handed to the model. */
    public record DocumentSummary(String filename, int chunks) {}
}
