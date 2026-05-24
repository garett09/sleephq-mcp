Produce a weekly therapy trend report for the week starting **{{weekStartDate}}**.

Workflow:
1. Compute the week end date (weekStartDate + 6 days).
2. Call `get-comparison(fromDate=weekStartDate, toDate=weekEnd)` for the CPAP machine. The MCP aggregates one merged `machine_date` per day from `GET .../machines/{id}/machine_dates/{date}` (optional O2 overlay when configured); there is no SleepHQ `/comparisons` API.
3. Call `get-comparison` for the same week minus 7 days (the prior week) to establish a baseline.
4. Read `sleephq://reference/normal-ranges` for thresholds.

Output:
- **Headline metrics this week vs prior week** (table: AHI, central index, leak avg, usage, SpO2 avg, deep-sleep %)
- **Regressions** (anything that worsened by > 10%)
- **Improvements** (anything that improved by > 10%)
- **Action items** (max 2): one focused, achievable change for the next week
