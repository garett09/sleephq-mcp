# Cross-validation findings

| Topic | Finding |
|-------|---------|
| Magic | `0xC73216AB` per `machine_common.h` — not `0x12345678` |
| `.000` s_last | Duration seconds *or* end epoch ms when `s_last > s_first` (parser handles both) |
| EDF date prefix | `YYYYMMDD` = session **start** local date; calendar query may be end night — resolve via session start |
| EDF mechanics | **PLD** for TidVol/RespRate/MinVent — not BRP |
| EDF paths | `DATALOG/{year}/YYYYMMDD_*_{suffix}.edf` |
| Sessions.info | Version 5 + format 2 on user disk; pairs are session_id + enabled byte |
| SleepHQ | No tidal_volume in `machine_date` API |
| LLM payloads | Server-side `night_analysis`; raw waveforms appendix-only |
| EVE vs `.000` dashboard | EVE timed labels (e.g. `Central Apnea`) may not match OSCAR summary event channels (`clear_airway`). Use `event_summary.counts` (EVE) and `summary_counts` (`.000` `m_cnt` hash) together; `summary_counts_source` = `oscar_summary_000`. |

## Deferred

- `sleephq_status: unavailable` while returning OSCAR-only combined night
- Full `.000` settings `QVariant` deserialization (EDF + xml index used as fallback)
- Per-burst PLMD windows (needs movement burst timestamps)
