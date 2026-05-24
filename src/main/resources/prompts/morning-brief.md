Produce a 3-line morning brief for last night.

Workflow:
1. Call `list-machine-dates(perPage=1, sortOrder="desc")` to get the most recent night for the CPAP machine.
2. Call `get-night-stats(machineDateId=...)` on that record.
3. Pull the O2 equivalent the same way using `SLEEPHQ_O2_MACHINE_ID`.

Output **exactly** this format — no preamble, no header:

```
AHI: <value>/hr  |  Central: <value>/hr  |  Leak avg: <value> L/min  |  Usage: <value> h  |  SpO2 avg: <value>%
Concern: <one short phrase, or "none">
Action: <one short imperative, or "no change">
```
