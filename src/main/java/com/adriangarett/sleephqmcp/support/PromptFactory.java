package com.adriangarett.sleephqmcp.support;

import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Loads {@code prompts/{name}.md} from the classpath, substitutes {{placeholders}},
 * and wraps the result as an MCP {@link McpSchema.GetPromptResult}.
 */
@Component
public class PromptFactory {

    private final ClinicalContent loader;

    public PromptFactory(ClinicalContent loader) {
        this.loader = loader;
    }

    public McpSchema.GetPromptResult build(String name, String description, Map<String, String> variables) {
        return build(name, description, variables, McpSchema.Role.USER);
    }

    public McpSchema.GetPromptResult build(String name, String description, Map<String, String> variables,
                                           McpSchema.Role role) {
        String template = loader.readClasspath("prompts/" + name + ".md");
        String rendered = render(template, variables);
        return new McpSchema.GetPromptResult(description, List.of(
                new McpSchema.PromptMessage(role, new McpSchema.TextContent(rendered))));
    }

    /**
     * Replaces every {@code {{key}}} occurrence in the template. Uses {@link String#replace(CharSequence, CharSequence)},
     * so placeholders that appear multiple times are all substituted (there is no single-occurrence mode).
     */
    static String render(String template, Map<String, String> variables) {
        String out = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            String value = entry.getValue() == null ? "" : entry.getValue();
            out = out.replace(placeholder, value);
        }
        return out;
    }
}
