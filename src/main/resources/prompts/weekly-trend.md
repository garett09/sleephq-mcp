Produce a weekly therapy trend report for the week starting **{{weekStartDate}}**.

Resources: `sleephq://reference/normal-ranges`, `sleephq://playbook/workflows`

Workflow:
1. Week end = weekStartDate + 6 calendar days (YYYY-MM-DD).
2. `get-comparison(fromDate=weekStartDate, toDate=weekEnd)` for CPAP machine (merged O2 + journal per night).
3. Prior week: `get-comparison(fromDate=<weekStart minus 7d>, toDate=<weekStart minus 1d>)`.
4. Build **Rolling 7-Day Performance Table** from `nights[]` (therapy + journal columns per Goose recipe balanced mode).

Output:
- **Headline metrics this week vs prior week** (table)
- **Findings with confidence**
- **Regressions** (>10% worse)
- **Improvements** (>10% better)
- **Recommendations** (max 2 actions with confidence)
- If any night AHI ≥5 or SpO₂ min <90%: note whether to run `get-device-events` + `scan-apnea-events` on worst night only
