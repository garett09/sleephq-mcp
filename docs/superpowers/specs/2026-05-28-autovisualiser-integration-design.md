# Goose autovisualiser integration — design

**Date:** 2026-05-28  
**Status:** Approved (design)  
**Scope:** Enable Sleep Therapy Advisor (Goose recipe + MCP playbook resource) to produce inline charts from existing SleepHQ MCP JSON — **no** server-side MCP-UI or Java chart generation.

## Background

- **autovisualiser** is a Goose **builtin** extension (client-side D3/Chart.js HTML). It charts structured data the agent passes after MCP tools return.
- **sleephq-mcp** already emits chart-friendly JSON: `get-comparison` `nights[].table_display` (numeric `ahi`, `spo2_pct`, `usage_hours`, `sleep_minutes`) plus root `apnea_trends`; `journal.sleep_stages_summary.minutes_by_stage_for_reporting`; `get-oscar-trend` slim rows.
- There is **no** autovisualiser code in this repo today; integration is recipe + agent instructions + optional MCP resource `sleephq://playbook/autovisualiser`.

## Non-goals

- No new `@McpTool` or `visualization_hints` JSON fields in Java (YAGNI until chart failures justify structured hints).
- No autovisualiser in smoke tests (`context/goose-smoke-mcp.txt` stays PASS/FAIL text only).
- No embedding API keys in recipe YAML (keep `${SLEEPHQ_MCP_API_KEY}`).

## Architecture

```
Goose recipe (goose-recipe.yaml)
  extensions: autovisualiser (builtin)
  instructions: when/how to chart + authority rules
        ↓
Agent reads sleephq://playbook/autovisualiser (classpath MD)
        ↓
MCP tools return JSON → agent extracts numeric series → autovisualiser tools
```

**Authority:** Markdown tables and **FINAL RECOMMENDATIONS** use MCP numbers; charts are supplementary. If chart and table disagree, table wins.

## Chart catalog (by workflow)

| Workflow | Max charts | Preferred MCP source | Chart type |
|----------|------------|----------------------|------------|
| `balanced` | 2 | `get-comparison` `nights[]` | Line: nightly total AHI (`table_display.ahi` or `data.attributes.ahi_summary`); optional line: leak 95th or SpO₂ min |
| `physician_titration_review` | 2 | `get-comparison` | Line: OSA + CSA per night (`ahi.oa`, `ahi.ca`); bar: usage hours |
| `clinical_deep_dive` | 1 | `get-combined-night-by-date` `journal` | Donut: `minutes_by_stage_for_reporting` |
| `mask_leak_with_pressure` | 1 | `get-comparison` 7d or focal night leak | Bar: leak 95th by night |
| `morning_brief_only` | 0 | — | Text only unless user asks for a chart |

**Do not chart:** raw `get-waveform-by-date` channel arrays (too large); `get-device-events` / `scan-apnea-events` event lists (use tables); smoke runs.

## Numeric field preference

Prefer structured nodes over parsing `*_cell` strings:

- `nights[].table_display.ahi` → `{ oa, ca, h, … }` (events/hr)
- `nights[].table_display.spo2_pct.min`, `.avg`
- `nights[].table_display.usage_hours`
- `nights[].table_display.sleep_minutes` or `journal.sleep_stages_summary.minutes_by_stage_for_reporting`
- Root `apnea_trends` for span bullets only (not a substitute for per-night series)

## Failure mode

If autovisualiser errors or loops, stop after one retry; deliver report with markdown tables only.

## Verification

Manual: `./run.sh` → Goose recipe `balanced` → confirm 1–2 inline charts after `get-comparison`, tables still present, numbers match JSON.
