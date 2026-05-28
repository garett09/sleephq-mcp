# Session decisions

| Date | Decision |
|------|----------|
| 2026-05 | Analyze on server; compact `night_analysis` to LLM |
| 2026-05 | Include all OSCAR channel types present per night (no arbitrary subset) |
| 2026-05 | `notable_moments` = extrema correlated with EVE events (±120s default) |
| 2026-05 | Primary tool: `get-combined-night-by-date` with `night_analysis` |
| 2026-05 | `get-oscar-events` default `detail=summary`; `full` opt-in |
| 2026-05 | PLMD best-effort via `movement_summary.av` until burst detector exists |
| 2026-05-28 | Journal stage mismatch on wide Apple Health span: use `minutes_by_stage_main_episode` + `journal_stage_mismatch` flag; contextual waveform via `WaveformWindowPlanner` |
