# Goose autovisualiser integration — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a reusable agent prompt + Goose recipe wiring so Sleep Therapy Advisor sessions can render 1–2 inline charts from live SleepHQ MCP JSON without changing MCP tool behavior.

**Architecture:** Goose-side only — add `autovisualiser` builtin to `goose-recipe.yaml`, publish chart rules as MCP resource `sleephq://playbook/autovisualiser`, cross-link in capabilities/CLAUDE/smoke docs. No Java logic changes beyond registering the new resource.

**Tech stack:** Markdown/YAML, Spring `@McpResource` (one method), Goose builtin `autovisualiser`, existing `get-comparison` / `get-combined-night-by-date` JSON.

**Design spec:** [`docs/superpowers/specs/2026-05-28-autovisualiser-integration-design.md`](../specs/2026-05-28-autovisualiser-integration-design.md)

---

## File map

| Action | File | Purpose |
|--------|------|---------|
| Create | `src/main/resources/clinical/autovisualiser.md` | Canonical agent prompt + JSON field map |
| Modify | `src/main/java/.../resources/StaticContextResources.java` | Expose `sleephq://playbook/autovisualiser` |
| Modify | `goose-recipe.yaml` | Extension + instructions + grounding read |
| Modify | `sleephq-mcp-capabilities.md` | Goose extensions section |
| Modify | `CLAUDE.md` | One-paragraph convention |
| Modify | `context/goose-smoke-mcp.txt` | Explicit SKIP autovisualiser |
| Modify | `src/main/resources/clinical/output-format.md` | Charts supplement tables (2 sentences) |

**No changes:** Java services, `@McpTool` signatures, smoke shell scripts, `mvn test` expectations (resource registration has no unit test today — manual MCP resource fetch).

---

## Task 1: Autovisualiser playbook (canonical prompt)

**Files:**
- Create: `src/main/resources/clinical/autovisualiser.md`

- [ ] **Step 1: Create the playbook file**

Paste this entire file (no placeholders):

```markdown
# Autovisualiser — SleepHQ MCP chart playbook

Use the Goose **autovisualiser** builtin **after** live MCP data returns this session. Charts supplement markdown tables; they never replace them.

## Authority

1. Every numeric label in a chart must come from MCP JSON returned **this session**.
2. Markdown tables (`*_cell`, **FINAL RECOMMENDATIONS**) stay authoritative.
3. If a chart disagrees with a table, fix or drop the chart — do not change table numbers.

## When to chart

| workflow_mode | Max charts | Call autovisualiser? |
|---------------|------------|----------------------|
| balanced | 2 | Yes — after `get-comparison` |
| physician_titration_review | 2 | Yes — after `get-comparison` |
| clinical_deep_dive | 1 | Yes — sleep stages after `get-combined-night-by-date` |
| mask_leak_with_pressure | 1 | Optional — 7d leak from `get-comparison` if called |
| morning_brief_only | 0 | No — unless user explicitly asks |
| smoke / PASS-FAIL checklists | 0 | **Never** |

## When not to chart

- Before the workflow primary data tool returns (`get-comparison` or `get-combined-night-by-date`).
- Raw waveform channel arrays (`get-waveform-by-date.channels`).
- Full EVE/scan event lists (keep tables in appendix).
- Invented or baseline (`sleephq://patient/baseline`) numbers.

## Data extraction (prefer structured fields)

### `get-comparison` — nightly series

Build arrays from `nights[]` where `skipped` is not true:

```json
{
  "dates": ["2026-05-19", "2026-05-20"],
  "ahi_total": [0.57, 0.42],
  "osa_per_hr": [0.2, 0.15],
  "csa_per_hr": [0.1, 0.08],
  "leak_95_l_min": [12.0, 8.5],
  "spo2_min_pct": [92, 94],
  "usage_hours": [7.2, 6.8]
}
```

**Source paths (in order):**

| Series | Primary path | Fallback |
|--------|--------------|----------|
| date | `nights[].date` | — |
| ahi_total | `nights[].table_display.ahi` → `av` or `average` | `nights[].data.attributes.ahi_summary` |
| osa_per_hr | `table_display.ahi.oa` | `ahi_summary.oa` |
| csa_per_hr | `table_display.ahi.ca` | `ahi_summary.ca` |
| leak_95_l_min | `table_display.leak_rate_l_min` → `p95` or `95` | parse `leak_cell` only if object absent |
| spo2_min_pct | `table_display.spo2_pct.min` | `spo2_cell` parse |
| usage_hours | `table_display.usage_hours` | `usage_cell` parse |

Skip nights with `skipped: true` or missing `data`.

### Sleep stages — single night

From `get-combined-night-by-date` → `journal.sleep_stages_summary`:

```json
{
  "title": "Sleep stages (main episode)",
  "segments": [
    { "label": "Awake", "minutes": 45 },
    { "label": "Light (core)", "minutes": 180 },
    { "label": "Deep", "minutes": 60 },
    { "label": "REM", "minutes": 90 }
  ],
  "source": "minutes_by_stage_for_reporting",
  "reporting_source": "main_sleep_episode"
}
```

Use `minutes_by_stage_for_reporting` only (not `minutes_by_stage` vs `minutes_by_stage_main_episode` pick). Map `core` → **Light (core)** in the legend.

### `get-oscar-trend` (optional second chart)

Only when OSCAR was already fetched and SleepHQ span lacks nights. Use `nights[].respiratory_indices` numeric fields — **do not** divide `events.summary_counts` by hours.

## Recommended charts

1. **Nightly control (line)** — x: date, y: AHI (/hr). Series: total AHI; optional second axis or second chart for OSA + CSA lines.
2. **Sleep stages (donut/pie)** — one focal night from `minutes_by_stage_for_reporting`.
3. **Leak trend (bar)** — x: date, y: 95th % leak (L/min) when discussing mask fit.

## Autovisualiser usage

1. Extract JSON arrays/objects as above from the latest tool result.
2. Call autovisualiser with a short title + the structured payload (chart type is inferred by the extension).
3. Place charts under `## Technologist read` after the markdown table for the same data.
4. Caption each chart: tool name + date span (e.g. `get-comparison 2026-05-19–25`).
5. On failure: one-line note "Chart skipped (autovisualiser error)" and continue — do not block the report.

## Example instruction to autovisualiser

> Line chart: nightly total AHI (events/hr) by date. Data: {"dates":["2026-05-19","2026-05-20"],"ahi_total":[0.57,0.42]}. Source: get-comparison table_display.ahi.
```

- [ ] **Step 2: Verify file is on classpath**

Run: `test -f src/main/resources/clinical/autovisualiser.md && wc -l src/main/resources/clinical/autovisualiser.md`  
Expected: file exists, ~100+ lines

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/clinical/autovisualiser.md
git commit -m "docs(clinical): add autovisualiser playbook for Goose charting"
```

---

## Task 2: Register MCP resource

**Files:**
- Modify: `src/main/java/com/adriangarett/sleephqmcp/resources/StaticContextResources.java`

- [ ] **Step 1: Add resource method after `output-format`**

Locate the existing block:

```java
    @McpResource(uri = "sleephq://playbook/output-format",
```

Immediately **after** that method's closing brace, add:

```java
    @McpResource(uri = "sleephq://playbook/autovisualiser",
            name = "Autovisualiser playbook",
            description = "When and how to chart SleepHQ MCP JSON with Goose autovisualiser (field paths, limits).",
            mimeType = "text/markdown")
    public String autovisualiserPlaybook() {
        return content.load("autovisualiser.md", "sleephq://playbook/autovisualiser");
    }
```

- [ ] **Step 2: Compile**

Run: `./mvnw -q compile`  
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/adriangarett/sleephqmcp/resources/StaticContextResources.java
git commit -m "feat(mcp): expose sleephq://playbook/autovisualiser resource"
```

---

## Task 3: Goose recipe — extension + instructions

**Files:**
- Modify: `goose-recipe.yaml`

- [ ] **Step 1: Add extension block**

In `extensions:`, after the `chatrecall` entry, append:

```yaml
  - type: builtin
    name: autovisualiser
    bundled: true
    timeout: 120
    description: >
      Inline charts from structured SleepHQ MCP JSON. Use only after get-comparison
      or get-combined-night-by-date returns; tables remain authoritative.
```

- [ ] **Step 2: Add Shared tool literacy bullet**

In `## Shared tool literacy (every session)`, after the waveform anchors bullet, add:

```markdown
  - *Autovisualiser (Goose builtin):* After primary MCP data returns, read `sleephq://playbook/autovisualiser` and render ≤2 charts (line AHI/OA/CA trends, sleep-stage donut, leak bar). Never chart before `get-comparison` / `get-combined-night-by-date`; never chart smoke tests; on failure continue with tables only.
```

- [ ] **Step 3: Add Response style subsection**

After `## Response style` bullets (before `prompt: |`), insert:

```markdown
  ## Autovisualiser (supplementary charts)

  After the workflow **Required** tools succeed:
  1. Read MCP resource **`sleephq://playbook/autovisualiser`**.
  2. Build chart payloads from numeric JSON paths listed there (`table_display.ahi`, `minutes_by_stage_for_reporting`, etc.) — not from memory.
  3. Render **at most 2** inline charts for `balanced` and `physician_titration_review`, **1** for `clinical_deep_dive`, **0** for `morning_brief_only` unless the user asks.
  4. Place charts immediately after the markdown table they summarize; caption with tool + date span.
  5. **Tables and FINAL RECOMMENDATIONS win** if chart and table disagree.

  {% if workflow_mode == "morning_brief_only" %}
  Do **not** call autovisualiser in this mode unless the user explicitly requests a chart.
  {% endif %}
```

- [ ] **Step 4: Extend grounding protocol**

In `## Grounding protocol`, step 2 static resources list, append:

`, sleephq://playbook/autovisualiser`

- [ ] **Step 5: Add activity line**

In `activities:`, after the OSCAR smoke activity, add:

```yaml
  - "Charts (optional): after get-comparison → read sleephq://playbook/autovisualiser → ≤2 autovisualiser charts; skip for morning_brief_only and smoke runs."
```

- [ ] **Step 6: Commit**

```bash
git add goose-recipe.yaml
git commit -m "feat(goose): wire autovisualiser extension and chart instructions"
```

---

## Task 4: Documentation cross-links

**Files:**
- Modify: `sleephq-mcp-capabilities.md`
- Modify: `CLAUDE.md`
- Modify: `context/goose-smoke-mcp.txt`
- Modify: `src/main/resources/clinical/output-format.md`

- [ ] **Step 1: capabilities.md — new section before Prompts**

Insert after the OSCAR table section:

```markdown
## Goose extensions (recipe / Desktop)

| Extension | Type | Role |
|-----------|------|------|
| `sleephq` | streamable_http → `http://localhost:8080/mcp` | All 36 MCP tools |
| `autovisualiser` | builtin | Inline charts from structured tool JSON (see `sleephq://playbook/autovisualiser`) |
| `memory` | builtin | Optional durable notes |
| `chatrecall` | platform | Prior session search — not a substitute for fresh MCP calls |

Chartable payloads: `get-comparison` `nights[].table_display` (numeric `ahi`, `spo2_pct`, `usage_hours`), `journal.sleep_stages_summary.minutes_by_stage_for_reporting`, optional `get-oscar-trend` indices. Recipe: [`goose-recipe.yaml`](goose-recipe.yaml).
```

- [ ] **Step 2: CLAUDE.md — new bullet under Key conventions**

```markdown
**Goose autovisualiser:** Optional client-side charts from MCP JSON; no server chart API. Agent rules: `src/main/resources/clinical/autovisualiser.md` (`sleephq://playbook/autovisualiser`). Wired in `goose-recipe.yaml`. Smoke tests skip charts.
```

- [ ] **Step 3: goose-smoke-mcp.txt — Rules block**

After line 8 (`If a tool errors...`), add:

```
- Do NOT use autovisualiser or render charts — smoke is PASS/FAIL text only.
```

- [ ] **Step 4: output-format.md — after confidence table**

```markdown
## Inline charts (Goose autovisualiser)

Optional **after** markdown tables exist for the same data. Max 2 charts on span reports; tables and **FINAL RECOMMENDATIONS** stay authoritative. See `sleephq://playbook/autovisualiser`.
```

- [ ] **Step 5: Commit**

```bash
git add sleephq-mcp-capabilities.md CLAUDE.md context/goose-smoke-mcp.txt src/main/resources/clinical/output-format.md
git commit -m "docs: cross-link Goose autovisualiser playbook and smoke exclusion"
```

---

## Task 5: Manual verification

- [ ] **Step 1: Start server**

Run: `./run.sh`  
Expected log: `Classpath sanity OK`

- [ ] **Step 2: Fetch resource (optional curl MCP or Goose)**

In Goose Desktop or CLI with sleephq extension: read resource `sleephq://playbook/autovisualiser`  
Expected: markdown playbook loads

- [ ] **Step 3: Recipe session**

Run Sleep Therapy Advisor with `workflow_mode=balanced`, autovisualiser enabled.  
Expected:

- `get-comparison` called before report
- ≤2 inline charts (e.g. nightly AHI line)
- Markdown table still present with matching numbers

- [ ] **Step 4: Smoke exclusion**

Run prompt from `context/goose-smoke-mcp.txt`  
Expected: no charts in output

- [ ] **Step 5: Stop server**

Run: `./stop.sh`

---

## Self-review (spec coverage)

| Requirement | Task |
|-------------|------|
| Canonical agent prompt | Task 1 `autovisualiser.md` |
| MCP resource URI | Task 2 |
| Goose recipe extension | Task 3 |
| Workflow chart limits | Task 1 table + Task 3 Jinja |
| Numeric JSON paths | Task 1 extraction table |
| Smoke exclusion | Task 4 |
| No Java tool changes | Plan scope |
| No secrets in YAML | Existing `${SLEEPHQ_MCP_API_KEY}` unchanged |

**Placeholder scan:** None.

---

## Execution handoff

Plan complete and saved to `docs/superpowers/plans/2026-05-28-autovisualiser-integration.md`. Two execution options:

1. **Subagent-Driven (recommended)** — fresh subagent per task, review between tasks  
2. **Inline Execution** — implement tasks in this session with checkpoints  

Which approach?
