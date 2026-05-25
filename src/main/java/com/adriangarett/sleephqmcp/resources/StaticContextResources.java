package com.adriangarett.sleephqmcp.resources;

import com.adriangarett.sleephqmcp.support.ClinicalContent;
import io.modelcontextprotocol.spec.McpSchema;
import org.springaicommunity.mcp.annotation.McpResource;
import org.springframework.stereotype.Component;

/**
 * Static clinical context from {@code src/main/resources/clinical/}.
 */
@Component
public class StaticContextResources {

    private final ClinicalContent content;

    public StaticContextResources(ClinicalContent content) {
        this.content = content;
    }

    @McpResource(uri = "sleephq://patient/baseline",
            name = "Patient baseline",
            description = "PSG baseline: unassisted AHI, RDI on CPAP, sleep architecture.",
            mimeType = "text/markdown")
    public McpSchema.ReadResourceResult patientBaseline() {
        return content.load("patient-baseline.md", "sleephq://patient/baseline");
    }

    @McpResource(uri = "sleephq://device/current",
            name = "Current device",
            description = "CPAP + O2 Ring configuration (machine IDs, mode, pressure, EPR, mask).",
            mimeType = "text/markdown")
    public McpSchema.ReadResourceResult deviceCurrent() {
        return content.load("device-current.md", "sleephq://device/current");
    }

    @McpResource(uri = "sleephq://guidelines/resmed-titration",
            name = "ResMed titration quick rules",
            description = "One-screen adult OSA triggers; links to full Therapy Handbook digest.",
            mimeType = "text/markdown")
    public McpSchema.ReadResourceResult resmedTitration() {
        return content.load("resmed-titration.md", "sleephq://guidelines/resmed-titration");
    }

    @McpResource(uri = "sleephq://guidelines/resmed-therapy-handbook",
            name = "ResMed Therapy Handbook (v07 digest)",
            description = "Condensed 10114280r1: CPAP/APAP titration, central/TECSA, leak, APAP home adaptation, SleepHQ mapping.",
            mimeType = "text/markdown")
    public McpSchema.ReadResourceResult resmedTherapyHandbook() {
        return content.load("resmed-therapy-handbook.md", "sleephq://guidelines/resmed-therapy-handbook");
    }

    @McpResource(uri = "sleephq://reference/normal-ranges",
            name = "Normal physiological ranges",
            description = "Reference ranges for AHI, leak, SpO2, usage.",
            mimeType = "text/markdown")
    public McpSchema.ReadResourceResult normalRanges() {
        return content.load("normal-ranges.md", "sleephq://reference/normal-ranges");
    }

    @McpResource(uri = "sleephq://playbook/workflows",
            name = "Workflow playbook",
            description = "Maps Goose workflow modes to MCP prompts and tools; payload caps.",
            mimeType = "text/markdown")
    public McpSchema.ReadResourceResult workflowPlaybook() {
        return content.load("workflow-playbook.md", "sleephq://playbook/workflows");
    }

    @McpResource(uri = "sleephq://playbook/data-sources",
            name = "Data source matrix",
            description = "Which MCP tool to trust for AHI, events, O2, trends; reconciliation rules.",
            mimeType = "text/markdown")
    public McpSchema.ReadResourceResult dataSourceMatrix() {
        return content.load("data-source-matrix.md", "sleephq://playbook/data-sources");
    }

    @McpResource(uri = "sleephq://playbook/output-format",
            name = "Report output format",
            description = "Required Markdown layout: bold FINAL RECOMMENDATIONS, confidence %, section order.",
            mimeType = "text/markdown")
    public McpSchema.ReadResourceResult outputFormat() {
        return content.load("output-format.md", "sleephq://playbook/output-format");
    }
}
