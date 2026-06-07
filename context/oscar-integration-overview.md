# OSCAR integration overview

## Configuration

```properties
oscar.data-path=${OSCAR_DATA_PATH:${user.home}/Documents/OSCAR_Data}
oscar.profile-name=${OSCAR_PROFILE_NAME:your-oscar-profile-name}
oscar.device-folder=${OSCAR_DEVICE_FOLDER:}  # optional override
```

Device folder resolution: `Profiles/{profile}/ResMed_*` (first match) unless `oscar.device-folder` is set.

## Data layers

1. **Summaries** — `Summaries.xml.gz`, `Summaries/{sessionId}.000`
2. **EDF backups** — `Backup/DATALOG/{year}/YYYYMMDD_*_{EVE,PLD,BRP}.edf`

## MCP tools

| Tool | Role |
|------|------|
| `get-combined-night-by-date` | SleepHQ + optional `night_analysis` |
| `get-night-analysis` | OSCAR-only compact analysis |
| `get-oscar-status` | Path resolution diagnostic |

## Implementation note

`.000` channel statistic hashes use a complex QDataStream settings block; Phase 1 uses EDF-derived stats plus `Summaries.xml.gz` for channel IDs.
