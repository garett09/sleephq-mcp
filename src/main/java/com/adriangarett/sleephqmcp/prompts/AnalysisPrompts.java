package com.adriangarett.sleephqmcp.prompts;

import com.adriangarett.sleephqmcp.support.PromptFactory;
import com.adriangarett.sleephqmcp.support.SleepHqPathParams;
import io.modelcontextprotocol.spec.McpSchema;
import org.springaicommunity.mcp.annotation.McpArg;
import org.springaicommunity.mcp.annotation.McpPrompt;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Each prompt is a 3-line wrapper around a Markdown template under
 * {@code src/main/resources/prompts/}. Tune the doctor by editing those files — no recompile.
 * Date arguments are validated as {@code YYYY-MM-DD} before templates are built.
 */
@Component
public class AnalysisPrompts {

    private final PromptFactory factory;

    public AnalysisPrompts(PromptFactory factory) {
        this.factory = factory;
    }

    @McpPrompt(name = "nightly-review",
            description = "Pull last night's stats, compare to 7-night average and patient baseline, flag threshold breaches.")
    public McpSchema.GetPromptResult nightlyReview(
            @McpArg(name = "date", description = "Night date YYYY-MM-DD", required = true) String date) {
        String d = SleepHqPathParams.requireCalendarDate(date, "date");
        return factory.build("nightly-review", "Nightly review", Map.of("date", d));
    }

    @McpPrompt(name = "central-apnea-investigation",
            description = "Examine central apnea trend over 14 nights, inspect flow rate around each central event, assess TECSA risk.")
    public McpSchema.GetPromptResult centralApneaInvestigation(
            @McpArg(name = "date", description = "Night date YYYY-MM-DD", required = true) String date) {
        String d = SleepHqPathParams.requireCalendarDate(date, "date");
        return factory.build("central-apnea-investigation", "Central apnea investigation", Map.of("date", d));
    }

    @McpPrompt(name = "weekly-trend",
            description = "Compare this week vs previous via get-comparison; summarize trends; flag regressions.")
    public McpSchema.GetPromptResult weeklyTrend(
            @McpArg(name = "weekStartDate", description = "Monday of the week YYYY-MM-DD (format only; not validated as Monday)", required = true) String weekStartDate) {
        String w = SleepHqPathParams.requireCalendarDate(weekStartDate, "weekStartDate");
        return factory.build("weekly-trend", "Weekly trend", Map.of("weekStartDate", w));
    }

    @McpPrompt(name = "leak-diagnosis",
            description = "Pull leak waveform + sessions, correlate spikes with mask on/off vs mid-session, recommend mask adjustment.")
    public McpSchema.GetPromptResult leakDiagnosis(
            @McpArg(name = "date", description = "Night date YYYY-MM-DD", required = true) String date) {
        String d = SleepHqPathParams.requireCalendarDate(date, "date");
        return factory.build("leak-diagnosis", "Leak diagnosis", Map.of("date", d));
    }

    @McpPrompt(name = "titration-decision",
            description = "Apply ResMed rules to last night's AHI/OAs/hypopneas/RERAs/snoring; recommend pressure adjustment (±1 cmH2O or no change).")
    public McpSchema.GetPromptResult titrationDecision(
            @McpArg(name = "date", description = "Night date YYYY-MM-DD", required = true) String date) {
        String d = SleepHqPathParams.requireCalendarDate(date, "date");
        return factory.build("titration-decision", "Titration decision", Map.of("date", d));
    }

    @McpPrompt(name = "o2-desat-review",
            description = "Pull SpO2 waveform and stats; identify desat events <90%; correlate with mask sessions and apnea events.")
    public McpSchema.GetPromptResult o2DesatReview(
            @McpArg(name = "date", description = "Night date YYYY-MM-DD", required = true) String date) {
        String d = SleepHqPathParams.requireCalendarDate(date, "date");
        return factory.build("o2-desat-review", "O2 desat review", Map.of("date", d));
    }

    @McpPrompt(name = "morning-brief",
            description = "Auto-pull last night's data and produce a 3-line morning brief: AHI, key concern (if any), one action item.")
    public McpSchema.GetPromptResult morningBrief() {
        return factory.build("morning-brief", "Morning brief", Map.of());
    }
}
