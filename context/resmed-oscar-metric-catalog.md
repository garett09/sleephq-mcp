# ResMed / OSCAR metric catalog

Channel IDs from OSCAR `schema.cpp`. MCP emits every id listed in session `channel_ids` / `m_availableChannels`.

## Waveforms (0x11xx)

| ID | Code | Unit |
|----|------|------|
| 0x1000 | usage_flag | — |
| 0x1100 | flow_rate | L/min |
| 0x1101 | mask_pressure | cmH2O |
| 0x1102 | flow_rate_hi_res | L/min |
| 0x1103 | tidal_volume | mL |
| 0x1158 | session_metric | — |
| 0x1104 | snore | — |
| 0x1105 | minute_vent | L/min |
| 0x1106 | resp_rate | bpm |
| 0x1108 | leak | L/min |
| 0x110C | pressure | cmH2O |
| 0x1116 | ahi | /h |
| 0x1119 | rdi | /h |

## Events (0x10xx)

| ID | Code |
|----|------|
| 0x1001 | clear_airway |
| 0x1002 | obstructive |
| 0x1003 | hypopnea |
| 0x1005 | flow_limit |

## EDF label mapping

| PLD label | Channel |
|-----------|---------|
| TidVol.2s | tidal_volume |
| RespRate.2s | resp_rate |
| MinVent.2s | minute_vent |
| MaskPress.2s | mask_pressure |

## SleepHQ gap

Tidal volume, minute ventilation, and detailed resp rate are **not** in SleepHQ `machine_date` — OSCAR local data fills this gap.
