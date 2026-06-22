package com.mittelstandgpt.mcp;

import com.mittelstandgpt.chat.KnowledgeBaseTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Re-exposes the knowledge-base retrieval tools over the Model Context Protocol
 * (stretch goal). The Spring AI MCP server (SSE/HTTP on the servlet stack)
 * advertises the tools registered here, so the same knowledge base is usable from
 * MCP clients such as Claude Desktop / Claude Code.
 *
 * <p>Reuses {@link KnowledgeBaseTools} unchanged: the {@code @Tool} methods are the
 * single source of truth for both in-process agentic tool calling and MCP, so MCP
 * never diverges from production retrieval behaviour. Gated by
 * {@code spring.ai.mcp.server.enabled} (default on; set {@code MI_MCP_ENABLED=false}
 * to disable).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "spring.ai.mcp.server",
        name = "enabled",
        matchIfMissing = true)
public class McpConfig {

    @Bean
    public ToolCallbackProvider knowledgeBaseToolCallbackProvider(KnowledgeBaseTools tools) {
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }
}
