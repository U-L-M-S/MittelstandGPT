package com.mittelstandgpt.document;

import java.time.Instant;

/** Metadata about an uploaded and ingested document, returned by the documents API. */
public record DocumentInfo(
        String filename,
        String contentType,
        long sizeBytes,
        int chunks,
        Instant uploadedAt) {}
