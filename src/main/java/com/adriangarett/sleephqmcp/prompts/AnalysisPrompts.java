package com.adriangarett.sleephqmcp.prompts;

import com.adriangarett.sleephqmcp.support.PromptFactory;
import com.adriangarett.sleephqmcp.support.SleepHqPathParams;
import io.modelcontextprotocol.spec.McpSchema;
import org.springaicommunity.mcp.annotation.McpArg;
import org.springaicommunity.mcp.annotation.McpPrompt;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * MCP prompts backed by Markdown templates under {@code src/main/resources/prompts/}.
 */
@Component
public class AnalysisPrompts {

    private final PromptFactory factory;

    public AnalysisPrompts(PromptFactory factory) {
        this.factory = factory;
    }

    @McpPrompt(name = "nightly-review",
            description = "Nightly review: combined night + 7d get-comparison, threshold breaches, confidence tables.")
    public McpSchema.GetPromptResult nightlyReview(
            @McpArg(name = "date", description = "Night date YYYY-MM-DD", required = true) String date) {
        String d = SleepHqPathParams.requireCalendarDate(date, "date");
        return factory.build("nightly-review", "Nightly review", Map.of("date", d));
    }

    @McpPrompt(name = "central-apnea-investigation",
            description = "14-night CA trend via get-comparison; EVE + waveform windows on focal date.")
    public McpSchema.GetPromptResult centralApneaInvestigation(
            @McpArg(name = "date", description = "Night date YYYY-MM-DD", required = true) String date) {
        String d = SleepHqPathParams.requireCalendarDate(date, "date");
        return factory.build("central-apnea-investigation", "Central apnea investigation", Map.of("date", d));
    }

    @McpPrompt(name = "weekly-trend",
            description = "7-day get-comparison vs prior week; rolling table and regressions.")
    public McpSchema.GetPromptResult weeklyTrend(
            @McpArg(name = "weekStartDate", description = "Week start YYYY-MM-DD", required = true) String weekStartDate) {
        String w = SleepHqPathParams.requireCalendarDate(weekStartDate, "weekStartDate");
        return factory.build("weekly-trend", "Weekly trend", Map.of("weekStartDate", w));
    }

    @McpPrompt(name = "leak-diagnosis",
            description = "Leak-focused night read, list-masks, optional waveform segment.")
    public McpSchema.GetPromptResult leakDiagnosis(
            @McpArg(name = "date", description = "Night date YYYY-MM-DD", required = true) String date) {
        String d = SleepHqPathParams.requireCalendarDate(date, "date");
        return factory.build("leak-diagnosis", "Leak diagnosis", Map.of("date", d));
    }

    @McpPrompt(name = "titration-decision",
            description = "Single-night ResMed titration rules with EVE cross-check.")
    public McpSchema.GetPromptResult titrationDecision(
            @McpArg(name = "date", description = "Night date YYYY-MM-DD", required = true) String date) {
        String d = SleepHqPathParams.requireCalendarDate(date, "date");
        return factory.build("titration-decision", "Titration decision", Map.of("date", d));
    }

    @McpPrompt(name = "o2-desat-review",
            description = "SpO2 summary + capped get-o2-oximetry segment; optional event correlation.")
    public McpSchema.GetPromptResult o2DesatReview(
            @McpArg(name = "date", description = "Night date YYYY-MM-DD", required = true) String date) {
        String d = SleepHqPathParams.requireCalendarDate(date, "date");
        return factory.build("o2-desat-review", "O2 desat review", Map.of("date", d));
    }

    @McpPrompt(name = "morning-brief",
            description = "3–4 line morning brief from get-combined-night-by-date.")
    public McpSchema.GetPromptResult morningBrief() {
        return factory.build("morning-brief", "Morning brief", Map.of());
    }

    @McpPrompt(name = "clinical-deep-dive",
            description = "Single-night EVE + flow scan + waveform + capped O2 with full reconciliation.")
    public McpSchema.GetPromptResult clinicalDeepDive(
            @McpArg(name = "date", description = "Night date YYYY-MM-DD", required = true) String date) {
        String d = SleepHqPathParams.requireCalendarDate(date, "date");
        return factory.build("clinical-deep-dive", "Clinical deep dive", Map.of("date", d));
    }

    @McpPrompt(name = "physician-titration-review",
            description = "15–90d get-comparison epoch review + selective deep nights (max 6).")
    public McpSchema.GetPromptResult physicianTitrationReview(
            @McpArg(name = "toDate", description = "Window end date YYYY-MM-DD", required = true) String toDate,
            @McpArg(name = "reviewSpanDays", description = "Span length 15, 30, 60, or 90", required = true) int reviewSpanDays) {
        String end = SleepHqPathParams.requireCalendarDate(toDate, "toDate");
        if (reviewSpanDays != 15 && reviewSpanDays != 30 && reviewSpanDays != 60 && reviewSpanDays != 90) {
            throw new IllegalArgumentException("reviewSpanDays must be 15, 30, 60, or 90");
        }
        return factory.build("physician-titration-review", "Physician titration review",
                Map.of("toDate", end, "reviewSpanDays", String.valueOf(reviewSpanDays)));
    }

    @McpPrompt(name = "event-reconciliation",
            description = "Side-by-side device EVE vs flow scan vs ahi_summary for one night.")
    public McpSchema.GetPromptResult eventReconciliation(
            @McpArg(name = "date", description = "Night date YYYY-MM-DD", required = true) String date) {
        String d = SleepHqPathParams.requireCalendarDate(date, "date");
        return factory.build("event-reconciliation", "Event reconciliation", Map.of("date", d));
    }
}
