package com.mittelstandgpt.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.mittelstandgpt.chat.KnowledgeBaseTools;
import com.mittelstandgpt.chat.RetrievalService;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallbackProvider;

/**
 * The MCP server advertises the knowledge-base tools. Verifies the
 * {@link ToolCallbackProvider} built from {@link KnowledgeBaseTools} exposes the
 * three retrieval tools by name — the same primitives the agentic loop uses.
 */
class McpToolExposureTest {

    @Test
    void exposesKnowledgeBaseToolsOverMcp() {
        KnowledgeBaseTools tools = new KnowledgeBaseTools(mock(RetrievalService.class));

        ToolCallbackProvider provider = new McpConfig().knowledgeBaseToolCallbackProvider(tools);

        var names =
                Arrays.stream(provider.getToolCallbacks())
                        .map(cb -> cb.getToolDefinition().name())
                        .toList();
        assertThat(names)
                .contains("searchKnowledgeBase", "listDocuments", "searchWithinDocument");
    }
}
