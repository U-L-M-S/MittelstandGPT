package com.mittelstandgpt.document;

import jakarta.annotation.PostConstruct;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

/**
 * Ingestion pipeline: extract text (Tika / page-aware PDF reader) → chunk with
 * overlap → embed → store in Qdrant.
 *
 * <p>The document listing is served from an in-memory registry that is rebuilt
 * from Qdrant on startup, so the list survives backend restarts (the chunks and
 * their file metadata live in Qdrant).
 */
@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

    /** Chunk metadata keys. {@code source}/{@code page} are also used for citations. */
    public static final String META_SOURCE = "source";
    public static final String META_PAGE = "page";
    public static final String META_CONTENT_TYPE = "contentType";
    public static final String META_SIZE = "sizeBytes";
    public static final String META_UPLOADED_AT = "uploadedAt";

    private static final int CHUNK_TOKENS = 800;
    private static final int OVERLAP_TOKENS = 150;

    private final VectorStore vectorStore;
    private final RestClient qdrantRest;
    private final String collection;
    private final OverlappingTokenTextSplitter splitter =
            new OverlappingTokenTextSplitter(CHUNK_TOKENS, OVERLAP_TOKENS);
    private final Map<String, DocumentInfo> registry = new ConcurrentHashMap<>();

    public DocumentIngestionService(
            VectorStore vectorStore,
            @Value("${app.qdrant.rest-url:}") String qdrantRestUrl,
            @Value("${spring.ai.vectorstore.qdrant.collection-name:mittelstandgpt}") String collection) {
        this.vectorStore = vectorStore;
        // The startup registry rebuild scrolls Qdrant's REST API directly, so it only
        // applies to the local (Qdrant) profile. Under the azure profile the URL is
        // blank and the rebuild is skipped; ingestion itself stays provider-agnostic
        // (vectorStore.add works on any VectorStore).
        this.qdrantRest =
                StringUtils.hasText(qdrantRestUrl)
                        ? RestClient.builder().baseUrl(qdrantRestUrl).build()
                        : null;
        this.collection = collection;
    }

    /** Parses, chunks, embeds and stores a single uploaded file. */
    public DocumentInfo ingest(MultipartFile file) throws IOException {
        String filename = StringUtils.cleanPath(
                Objects.requireNonNullElse(file.getOriginalFilename(), "unbenannt"));
        String contentType = file.getContentType();
        long size = file.getSize();
        Instant uploadedAt = Instant.now();

        File temp = File.createTempFile("mittelstandgpt-upload-", "-" + filename);
        try {
            file.transferTo(temp);
            Resource resource = new FileSystemResource(temp);

            List<Document> pages = read(resource, filename);
            List<Document> normalized = withMetadata(pages, filename, contentType, size, uploadedAt);
            List<Document> chunks = splitter.apply(normalized);

            if (chunks.isEmpty()) {
                throw new EmptyDocumentException(filename);
            }

            vectorStore.add(chunks);
            log.info("Ingested '{}' as {} chunk(s)", filename, chunks.size());

            DocumentInfo info = new DocumentInfo(filename, contentType, size, chunks.size(), uploadedAt);
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

    /** Rebuilds documents with normalized text and clean metadata (file + page). */
    private List<Document> withMetadata(
            List<Document> docs, String filename, String contentType, long size, Instant uploadedAt) {
        List<Document> result = new ArrayList<>(docs.size());
        for (Document doc : docs) {
            String text = normalizeWhitespace(doc.getText());
            if (text.isBlank()) {
                continue;
            }
            Map<String, Object> metadata = new HashMap<>(doc.getMetadata());
            metadata.put(META_SOURCE, filename);
            // Stored as strings: the Qdrant store does not accept Long values.
            metadata.put(META_SIZE, String.valueOf(size));
            metadata.put(META_UPLOADED_AT, String.valueOf(uploadedAt.toEpochMilli()));
            if (contentType != null) {
                metadata.put(META_CONTENT_TYPE, contentType);
            }
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

    /**
     * On startup, scroll the Qdrant collection and group points by source file to
     * repopulate the registry, so the document list survives backend restarts.
     * Best-effort: a failure (e.g. empty/new collection) just leaves the list empty.
     */
    @PostConstruct
    void rebuildRegistryFromQdrant() {
        if (qdrantRest == null) {
            // Not on the Qdrant profile (e.g. azure): nothing to rebuild from here.
            return;
        }
        try {
            Map<String, Accumulator> bySource = new HashMap<>();
            Object offset = null;
            int pages = 0;
            do {
                Map<String, Object> body = new HashMap<>();
                body.put("limit", 256);
                body.put("with_payload", true);
                body.put("with_vector", false);
                if (offset != null) {
                    body.put("offset", offset);
                }
                Map<?, ?> response = qdrantRest
                        .post()
                        .uri("/collections/{collection}/points/scroll", collection)
                        .body(body)
                        .retrieve()
                        .body(Map.class);
                Map<?, ?> result = asMap(response == null ? null : response.get("result"));
                if (result == null) {
                    break;
                }
                for (Object point : asList(result.get("points"))) {
                    Map<?, ?> payload = asMap(asMap(point).get("payload"));
                    Object source = payload == null ? null : payload.get(META_SOURCE);
                    if (source != null) {
                        bySource.computeIfAbsent(source.toString(), k -> new Accumulator(payload)).count++;
                    }
                }
                offset = result.get("next_page_offset");
            } while (offset != null && ++pages < 10_000);

            bySource.forEach((source, acc) -> registry.put(source, acc.toInfo(source)));
            if (!registry.isEmpty()) {
                log.info("Rebuilt document registry from Qdrant: {} document(s)", registry.size());
            }
        } catch (Exception e) {
            log.warn("Could not rebuild document registry from Qdrant: {}", e.getMessage());
        }
    }

    private static Map<?, ?> asMap(Object value) {
        return value instanceof Map<?, ?> map ? map : null;
    }

    private static List<?> asList(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    /** Aggregates the chunks of one source file while scanning Qdrant. */
    private static final class Accumulator {
        private final String contentType;
        private final long sizeBytes;
        private final Instant uploadedAt;
        private int count = 0;

        Accumulator(Map<?, ?> payload) {
            Object ct = payload.get(META_CONTENT_TYPE);
            this.contentType = ct != null ? ct.toString() : null;
            this.sizeBytes = parseLong(payload.get(META_SIZE));
            long millis = parseLong(payload.get(META_UPLOADED_AT));
            this.uploadedAt = millis > 0 ? Instant.ofEpochMilli(millis) : Instant.EPOCH;
        }

        private static long parseLong(Object value) {
            if (value instanceof Number n) return n.longValue();
            if (value != null) {
                try {
                    return Long.parseLong(value.toString());
                } catch (NumberFormatException ignored) {
                    // fall through
                }
            }
            return 0L;
        }

        DocumentInfo toInfo(String source) {
            return new DocumentInfo(source, contentType, sizeBytes, count, uploadedAt);
        }
    }

    /** Raised when a file yields no extractable text. */
    public static class EmptyDocumentException extends RuntimeException {
        public EmptyDocumentException(String filename) {
            super("Aus der Datei '" + filename + "' konnte kein Text extrahiert werden.");
        }
    }
}
