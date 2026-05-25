Produce a morning brief for the most recent completed night.

Resources: `sleephq://device/current`, `sleephq://reference/normal-ranges`

Workflow:
1. Discovery → resolve latest night date (or use user-provided date).
2. `get-combined-night-by-date(date)` — AHI, leak, usage, SpO₂, `journal` steps/stages if present.
3. Optional MCP prompt `nightly-review` context only if user asks for 7-day comparison.

Output (4 lines max):
- **AHI** (value + Confidence: High/Medium + source tool)
- **Key concern** (or "none")
- **One action**
- **Journal** (steps / sleep stages one line, or omit)
