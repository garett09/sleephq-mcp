package com.adriangarett.sleephqmcp.resources;

import com.adriangarett.sleephqmcp.support.ClinicalContent;
import io.modelcontextprotocol.spec.McpSchema;
import org.springaicommunity.mcp.annotation.McpResource;
import org.springframework.stereotype.Component;

/**
 * Static clinical context. Each resource maps 1:1 to a Markdown file under
 * {@code src/main/resources/clinical/}. Adding a new static resource = drop a {@code .md}
 * file there and add one 3-line method here.
 */
@Component
public class StaticContextResources {

    private final ClinicalContent content;

    public StaticContextResources(ClinicalContent content) {
        this.content = content;
    }

    @McpResource(uri = "sleephq://patient/baseline",
            name = "Patient baseline",
            description = "Adrian's PSG baseline (LCP, Aug 2023): unassisted AHI, RDI on CPAP, PLMD history, sleep architecture.",
            mimeType = "text/markdown")
    public McpSchema.ReadResourceResult patientBaseline() {
        return content.load("patient-baseline.md", "sleephq://patient/baseline");
    }

    @McpResource(uri = "sleephq://device/current",
            name = "Current device",
            description = "Active CPAP + O2 Ring configuration (machine IDs, mode, fixed pressure, EPR setting, mask).",
            mimeType = "text/markdown")
    public McpSchema.ReadResourceResult deviceCurrent() {
        return content.load("device-current.md", "sleephq://device/current");
    }

    @McpResource(uri = "sleephq://guidelines/resmed-titration",
            name = "ResMed titration rules",
            description = "Pressure-adjustment thresholds, EPR/TECSA warnings, central apnea escalation rules from the ResMed Therapy Handbook.",
            mimeType = "text/markdown")
    public McpSchema.ReadResourceResult resmedTitration() {
        return content.load("resmed-titration.md", "sleephq://guidelines/resmed-titration");
    }

    @McpResource(uri = "sleephq://reference/normal-ranges",
            name = "Normal physiological ranges",
            description = "Reference ranges for AHI, leak rate, SpO2, etc. for quick interpretation.",
            mimeType = "text/markdown")
    public McpSchema.ReadResourceResult normalRanges() {
        return content.load("normal-ranges.md", "sleephq://reference/normal-ranges");
    }
}
