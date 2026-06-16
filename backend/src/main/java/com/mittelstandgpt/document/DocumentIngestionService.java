package com.mittelstandgpt.document;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * Ingestion pipeline: extract text (Tika / page-aware PDF reader) → chunk with
 * overlap → embed → store in Qdrant. Keeps an in-memory registry of uploads for
 * the listing endpoint; the chunks themselves live in Qdrant.
 */
@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

    /** Metadata keys stored on every chunk (used for source citations in Phase 3). */
    public static final String META_SOURCE = "source";
    public static final String META_PAGE = "page";

    private static final int CHUNK_TOKENS = 800;
    private static final int OVERLAP_TOKENS = 150;

    private final VectorStore vectorStore;
    private final OverlappingTokenTextSplitter splitter =
            new OverlappingTokenTextSplitter(CHUNK_TOKENS, OVERLAP_TOKENS);
    private final Map<String, DocumentInfo> registry = new ConcurrentHashMap<>();

    public DocumentIngestionService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /** Parses, chunks, embeds and stores a single uploaded file. */
    public DocumentInfo ingest(MultipartFile file) throws IOException {
        String filename = StringUtils.cleanPath(
                Objects.requireNonNullElse(file.getOriginalFilename(), "unbenannt"));

        File temp = File.createTempFile("mittelstandgpt-upload-", "-" + filename);
        try {
            file.transferTo(temp);
            Resource resource = new FileSystemResource(temp);

            List<Document> pages = read(resource, filename);
            List<Document> normalized = withSourceMetadata(pages, filename);
            List<Document> chunks = splitter.apply(normalized);

            if (chunks.isEmpty()) {
                throw new EmptyDocumentException(filename);
            }

            vectorStore.add(chunks);
            log.info("Ingested '{}' as {} chunk(s)", filename, chunks.size());

            DocumentInfo info = new DocumentInfo(
                    filename, file.getContentType(), file.getSize(), chunks.size(), Instant.now());
            registry.put(filename, info);
            return info;
        } finally {
            if (!temp.delete()) {
                temp.deleteOnExit();
            }
        }
    }

    /** Uploaded documents, newest first. */
    public List<DocumentInfo> list() {
        return registry.values().stream()
                .sorted(Comparator.comparing(DocumentInfo::uploadedAt).reversed())
                .toList();
    }

    private List<Document> read(Resource resource, String filename) {
        if (filename.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            // One Document per page → accurate page numbers for citations.
            PdfDocumentReaderConfig config =
                    PdfDocumentReaderConfig.builder().withPagesPerDocument(1).build();
            return new PagePdfDocumentReader(resource, config).get();
        }
        // DOCX / TXT / others: Apache Tika handles them in one reader.
        return new TikaDocumentReader(resource).get();
    }

    /** Rebuilds documents with normalized text and clean metadata (source file + page). */
    private List<Document> withSourceMetadata(List<Document> docs, String filename) {
        List<Document> result = new ArrayList<>(docs.size());
        for (Document doc : docs) {
            String text = normalizeWhitespace(doc.getText());
            if (text.isBlank()) {
                continue;
            }
            Map<String, Object> metadata = new HashMap<>(doc.getMetadata());
            metadata.put(META_SOURCE, filename);
            Object pageNumber = metadata.get("page_number"); // set by PagePdfDocumentReader
            if (pageNumber != null) {
                metadata.put(META_PAGE, pageNumber);
            }
            result.add(Document.builder().text(text).metadata(metadata).build());
        }
        return result;
    }

    /**
     * Collapses the runs of whitespace that PDF text extraction inserts to preserve
     * visual layout, while keeping line/paragraph structure — improves embedding and
     * retrieval quality and makes source snippets readable.
     */
    private static String normalizeWhitespace(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("[ \\t\\x0B\\f\\r]+", " ") // collapse horizontal whitespace
                .replaceAll(" *\\n *", "\n") // trim around line breaks
                .replaceAll("\\n{3,}", "\n\n") // collapse excess blank lines
                .strip();
    }

    /** Raised when a file yields no extractable text. */
    public static class EmptyDocumentException extends RuntimeException {
        public EmptyDocumentException(String filename) {
            super("Aus der Datei '" + filename + "' konnte kein Text extrahiert werden.");
        }
    }
}
