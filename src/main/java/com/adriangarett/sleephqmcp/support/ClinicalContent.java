package com.adriangarett.sleephqmcp.support;

import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Loads a Markdown file from the classpath ({@code clinical/} by default) and wraps it
 * as an MCP {@link McpSchema.ReadResourceResult}.
 */
@Component
public class ClinicalContent {

    public McpSchema.ReadResourceResult load(String filename, String uri) {
        return load("clinical/", filename, uri, "text/markdown");
    }

    public McpSchema.ReadResourceResult load(String folder, String filename, String uri, String mimeType) {
        String text = readClasspath(folder + filename);
        return new McpSchema.ReadResourceResult(List.of(
                new McpSchema.TextResourceContents(uri, mimeType, text)));
    }

    public String readClasspath(String path) {
        try (var in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load classpath resource: " + path, e);
        }
    }
}
