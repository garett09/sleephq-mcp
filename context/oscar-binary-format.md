# OSCAR binary format

## Magic

All OSCAR machine files use `0xC73216AB` (little-endian `ab 16 32 c7`).

## Summaries (`*.000`)

| Offset | Field | Type | Notes |
|--------|-------|------|-------|
| 0 | magic | uint32 | `0xC73216AB` |
| 4 | version | uint16 | e.g. 18 |
| 6 | file_type | uint16 | 0 = summary |
| 8 | machine_id | uint32 | |
| 12 | session_id | uint32 | hex filename stem |
| 16 | s_first | int64 | start epoch **milliseconds** |
| 24 | s_last | int64 | duration **seconds** *or* end epoch **ms** when `s_last > s_first` and `s_last ≥ 1e12` (ResMed backup) |

Body: Qt `QDataStream` hashes (`settings`, `m_cnt`, `m_avg`, `m_min`, `m_max`, …). Settings use `QVariant` per channel; parser may fall back to EDF stats when hash deserialization fails.

**Tail:** `m_availableChannels` — uint32 count + channel ids at end of file (scanned backwards).

## Sessions.info

```
magic (uint32)
version (uint16)  — observed 5 on user data
format (uint16)   — 2
count (uint32)
repeat: session_id (uint32), enabled (uint8)
```

## Fast index

`Summaries.xml.gz` — session id, first/last ms, channel list, settings keys.

## References

- [OSCAR-code session.cpp](https://gitlab.com/CrimsonNape/OSCAR-code/-/tree/master/SleepLib)
- [machine_common.h](https://gitlab.com/CrimsonNape/OSCAR-code/-/blob/master/SleepLib/machine_common.h)
