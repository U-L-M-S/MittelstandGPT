package com.mittelstandgpt.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mittelstandgpt.document.DocumentIngestionService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.ai.document.Document;

/**
 * Loads the self-contained, offline evaluation assets from the repository's
 * {@code eval/} directory: the golden {@code dataset.jsonl} and the {@code corpus/}
 * fixture documents. Resolves the directory whether the build runs from the repo
 * root or the {@code backend/} module.
 */
public final class EvalDataset {

    private EvalDataset() {}

    /** Locate {@code eval/} relative to the current working directory. */
    public static Path resolveEvalDir() {
        for (String candidate : List.of("eval", "../eval")) {
            Path dir = Path.of(candidate);
            if (Files.isDirectory(dir) && Files.exists(dir.resolve("dataset.jsonl"))) {
                return dir.toAbsolutePath().normalize();
            }
        }
        throw new IllegalStateException(
                "eval/ directory with dataset.jsonl not found (looked in 'eval' and '../eval')");
    }

    /** Parse the JSONL dataset, one {@link EvalCase} per non-blank line. */
    public static List<EvalCase> loadCases(Path evalDir, ObjectMapper mapper) throws IOException {
        List<EvalCase> cases = new ArrayList<>();
        for (String line : Files.readAllLines(evalDir.resolve("dataset.jsonl"))) {
            if (!line.isBlank()) {
                cases.add(mapper.readValue(line, EvalCase.class));
            }
        }
        return cases;
    }

    /** One {@link Document} per corpus file, tagged with its filename as the source. */
    public static List<Document> loadCorpus(Path evalDir) throws IOException {
        List<Document> docs = new ArrayList<>();
        try (Stream<Path> files = Files.list(evalDir.resolve("corpus"))) {
            for (Path file : files.sorted().toList()) {
                String text = Files.readString(file);
                docs.add(
                        Document.builder()
                                .text(text)
                                .metadata(
                                        Map.of(
                                                DocumentIngestionService.META_SOURCE,
                                                file.getFileName().toString()))
                                .build());
            }
        }
        return docs;
    }
}
