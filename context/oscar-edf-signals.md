# OSCAR EDF signals (ResMed backup)

## Path layout

```
{device}/Backup/DATALOG/{year}/YYYYMMDD_HHMMSS_{suffix}.edf
```

Example: `Backup/DATALOG/2026/20260520_210920_PLD.edf`

## Suffix roles

| Suffix | Signals (typical) |
|--------|-------------------|
| `_BRP.edf` | Flow.40ms, Press.40ms |
| `_PLD.edf` | TidVol.2s, RespRate.2s, MinVent.2s, MaskPress.2s, Snore.2s, FlowLim.2s |
| `_EVE.edf` | EDF+ annotations (apnea/hypopnea flags) |
| `_SA2.edf` | Pulse.1s, SpO2.1s (device oximetry) |

**Mechanics (Vt, RR, MV):** use **`_PLD.edf`**, not `_BRP.edf`.

## MCP usage

- Parsed with existing `support/EdfParser` and `EdfAnnotationParser`
- Never returned raw in `night_analysis` — only aggregates and capped event samples
