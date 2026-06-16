package com.mittelstandgpt.document;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.transformer.splitter.TextSplitter;

/**
 * Splits text into ~{@code chunkSize}-token windows with {@code overlap} tokens
 * shared between consecutive chunks.
 *
 * <p>Extends Spring AI's {@link TextSplitter} so that document metadata (e.g. the
 * source file name and page number) is automatically copied onto every chunk.
 */
public class OverlappingTokenTextSplitter extends TextSplitter {

    private final Encoding encoding =
            Encodings.newLazyEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);
    private final int chunkSize;
    private final int overlap;

    public OverlappingTokenTextSplitter(int chunkSize, int overlap) {
        if (overlap >= chunkSize) {
            throw new IllegalArgumentException("overlap must be smaller than chunkSize");
        }
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    @Override
    protected List<String> splitText(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        IntArrayList tokens = encoding.encodeOrdinary(text);
        int total = tokens.size();
        int step = chunkSize - overlap;

        List<String> chunks = new ArrayList<>();
        for (int start = 0; start < total; start += step) {
            int end = Math.min(start + chunkSize, total);
            IntArrayList window = new IntArrayList(end - start);
            for (int i = start; i < end; i++) {
                window.add(tokens.get(i));
            }
            String chunk = encoding.decode(window).strip();
            if (!chunk.isBlank()) {
                chunks.add(chunk);
            }
            if (end == total) {
                break;
            }
        }
        return chunks;
    }
}
