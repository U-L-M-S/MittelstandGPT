package com.mittelstandgpt.document;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Upload and list documents. */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);

    private final DocumentIngestionService ingestion;

    public DocumentController(DocumentIngestionService ingestion) {
        this.ingestion = ingestion;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Bitte eine Datei auswählen."));
        }
        try {
            DocumentInfo info = ingestion.ingest(file);
            return ResponseEntity.status(HttpStatus.CREATED).body(info);
        } catch (DocumentIngestionService.EmptyDocumentException e) {
            return ResponseEntity.unprocessableEntity().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Ingestion failed for '{}'", file.getOriginalFilename(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Die Datei konnte nicht verarbeitet werden."));
        }
    }

    @GetMapping
    public List<DocumentInfo> list() {
        return ingestion.list();
    }
}
