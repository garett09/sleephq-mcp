package com.adriangarett.sleephqmcp.support;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PromptFactoryTest {

    @Test
    void render_substitutesAllPlaceholders() {
        String template = "Review for {{date}}, focus on {{focus}}.";
        String out = PromptFactory.render(template, Map.of("date", "2026-05-23", "focus", "central apnea"));
        assertThat(out).isEqualTo("Review for 2026-05-23, focus on central apnea.");
    }

    @Test
    void render_leavesUnknownPlaceholdersIntact() {
        String template = "Hello {{name}}, today is {{date}}.";
        String out = PromptFactory.render(template, Map.of("name", "Adrian"));
        assertThat(out).isEqualTo("Hello Adrian, today is {{date}}.");
    }

    @Test
    void render_treatsNullValueAsEmpty() {
        java.util.HashMap<String, String> vars = new java.util.HashMap<>();
        vars.put("note", null);
        String out = PromptFactory.render("Note: {{note}}", vars);
        assertThat(out).isEqualTo("Note: ");
    }
}
