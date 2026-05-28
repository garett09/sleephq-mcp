# Contextual waveform windows — Goose smoke test

Prerequisites:

- `.env` with `SLEEPHQ_CLIENT_ID`, `SLEEPHQ_CLIENT_SECRET`, `SLEEPHQ_MCP_API_KEY`
- SleepHQ account has BRP for the test night

## Goose Desktop (recommended)

1. **Terminal — start MCP**

   ```bash
   cd /path/to/sleephq-mcp
   ./run.sh
   ```

   Wait for `Classpath sanity OK`. Leave this terminal open.

2. **Goose Desktop — enable the SleepHQ extension**

   Settings → **Extensions** → add or edit a **Streamable HTTP** extension:

   | Field | Value |
   |-------|--------|
   | Name | `sleephq` (any label is fine) |
   | URL | `http://localhost:8080/mcp` |
   | Header | `X-SleepHQ-MCP-Key` = same value as `SLEEPHQ_MCP_API_KEY` in `.env` |

   Turn the extension **on** for this chat. You should see SleepHQ tools (e.g. `get-waveform-by-date`) in the tool list.

   *Same shape as `extensions:` in [`goose-recipe.yaml`](../goose-recipe.yaml). Desktop does not load that recipe file automatically — configure the extension in the app.*

3. **New chat** → paste the full prompt from [`context/goose-smoke-waveform.txt`](../context/goose-smoke-waveform.txt) (or the long checklist below). Send.

4. **Terminal — stop MCP when finished**

   ```bash
   ./stop.sh
   ```

**Optional recipe (Desktop):** If your Goose build supports importing recipes, open [`goose-recipe.yaml`](../goose-recipe.yaml) for the full sleep advisor — the smoke test is only the short paste prompt above, not the whole recipe.

---

## Goose CLI (optional)

```bash
./scripts/goose-smoke-waveform.sh
TEST_DATE=2026-05-19 ./scripts/goose-smoke-waveform.sh
```

## Automated curl (no Goose)

```bash
./run.sh
./scripts/smoke-waveform-mcp.sh 2026-05-19
./stop.sh
```

---

## Full Goose prompt (copy everything inside the fence)

Use this in Desktop if you want the detailed PASS/FAIL phases (the short prompt is in `context/goose-smoke-waveform.txt`).

---

## Goose prompt (copy everything inside the fence)

```
Contextual waveform windows — smoke test only.

Rules:
- Call MCP tools only; do not invent numbers.
- No clinical interpretation — PASS/FAIL checklist only.
- If a tool errors, record FAIL + exact error text and continue.
- TEST_DATE = 2026-05-19 unless you confirm another night has BRP via list-machine-dates.

## Phase 0 — connectivity

1. GET http://localhost:8080/actuator/health (or assume MCP extension connected)
   PASS if: UP / 200

0b. get-configured-defaults — PASS if `mcp_payload_hints.waveform_default_max_minutes` matches `.env` (restart `./run.sh` after editing `.env`)

## Phase 1 — contextual waveform (window_selection)

2. get-waveform-by-date(date=TEST_DATE, anchor=auto) — **omit maxMinutes** (uses `.env` default, e.g. 480)
   | Check | PASS when |
   |-------|-----------|
   | window_selection present | top-level `window_selection` object exists |
   | no minute-0 default | `window_selection.start_minute` is not 0 OR `anchor_resolved` is `manual` |
   | anchor_resolved | one of: eve_scan_overlap, worst_obstructive, worst_central, worst_leak, notable_moment, worst_spo2, manual |
   | reason + evidence | `reason` non-empty; `evidence` is an array (may be empty only if anchor still resolved) |
   | channels | `channels` array present (waveform payload OK) |

3. get-waveform-by-date(date=TEST_DATE, anchor=worst_leak) — omit maxMinutes
   | Check | PASS when |
   |-------|-----------|
   | resolved anchor | `window_selection.anchor_resolved` === `worst_leak` |
   | leak evidence | at least one `evidence[]` entry mentions leak OR reason references leak/flow |

4. get-waveform-by-date(date=TEST_DATE, anchor=eve_scan_overlap) — omit maxMinutes
   PASS if: returns waveform OR structured error `no_anchor_candidates` (acceptable when no EVE∩scan overlap on that night)
   FAIL if: silent `start_minute` 0 without `window_selection`

5. Manual override
   Call get-waveform-by-date(date=TEST_DATE, startMinute=<copy start_minute from step 2>, maxMinutes=15)
   PASS if: `window_selection.anchor_resolved` === `manual`

## Phase 2 — journal parity (combined night)

6. get-combined-night-by-date(date=TEST_DATE)
   Let `j` = `journal.sleep_stages_summary` (or nested journal path returned).
   | Check | PASS when |
   |-------|-----------|
   | summary exists | `j` is an object |
   | parity fields | `overlap_detected` (bool), `journal_stage_mismatch` (bool), `minutes_by_stage_main_episode` object |
   | naive vs episode | `minutes_by_stage_naive` OR `minutes_by_stage` present |
   | ui note when mismatch | if `journal_stage_mismatch` true → `ui_parity_note` non-empty |
   | night_analysis | when OSCAR configured: `night_analysis` present; else note SKIP |

## Phase 3 — stage anchors (optional / gated)

7. get-waveform-by-date(date=TEST_DATE, anchor=rem, maxMinutes=10)
   PASS if: waveform with `window_selection` OR error `no_stage_overlap` with message (not minute 0)
   Repeat informational only for `anchor=deep` if step 7 PASS.

## Deliverable

Reply with markdown:

### Smoke test — waveform windows (TEST_DATE)

| Step | Result | Notes |
|------|--------|-------|
| 0 health | PASS/FAIL | |
| 2 auto | PASS/FAIL | anchor_resolved=… start_minute=… |
| 3 worst_leak | PASS/FAIL | |
| 4 eve_scan_overlap | PASS/FAIL/SKIP | |
| 5 manual | PASS/FAIL | |
| 6 journal | PASS/FAIL | mismatch=… overlap=… |
| 7 rem | PASS/FAIL/SKIP | |

**OVERALL:** PASS only if steps 2, 3, 5, 6 PASS (4 and 7 may SKIP with documented reason).

Include a compact JSON appendix:
```json
{
  "test_date": "…",
  "auto_window_selection": { … },
  "worst_leak_window_selection": { … },
  "journal_sleep_stages_summary": { … }
}
```
```

---

## Expected on 2026-05-19 (reference)

From live validation (your account may differ slightly):

| Call | Typical |
|------|---------|
| `anchor=auto` | `worst_obstructive` or `worst_leak` when no EVE∩scan overlap; `start_minute` > 0 |
| `anchor=worst_leak` | evidence near **01:37** leak cluster; `start_minute` ~145 |
| journal | `journal_stage_mismatch: true`, `overlap_detected: true`, `ui_parity_note` set |

## Troubleshooting

| Symptom | Action |
|---------|--------|
| `no_sleephq_brp` | Pick a date with CPAP BRP in SleepHQ; check `SLEEPHQ_CPAP_MACHINE_ID` |
| `no_anchor_candidates` on `auto` | Try explicit `worst_leak` / `worst_obstructive`; confirm EVE import for that night |
| Goose still sends maxMinutes=10 | Smoke prompt used to force 10; **omit maxMinutes** so server uses `.env` (480). Re-paste `context/goose-smoke-waveform.txt`. |
| Defaults still 10 after .env change | Restart `./run.sh`; call `get-configured-defaults` to verify |
| Missing `minutes_by_stage_for_reporting` / `summary_schema_version` | **Stale JVM** — `./stop.sh && ./run.sh` (not a schema alias; fields added in current code) |
| Goose cannot reach MCP | `./run.sh`; Desktop: extension URL `http://localhost:8080/mcp` + `X-SleepHQ-MCP-Key` header |
| Tools missing in Desktop | Enable the sleephq extension for this session; confirm health: `curl http://localhost:8080/actuator/health` |
| Server left running | `./stop.sh` |

See also: [contextual waveform design spec](superpowers/specs/2026-05-28-contextual-waveform-windows-design.md), [OSCAR smoke](smoke-test-oscar-mcp.md).
