# Required report formatting (Goose / Markdown)

## Confidence scale (always show BOTH label and %)

| Label | Percentage | When to use |
|-------|------------|-------------|
| **High** | **85–95%** | Multiple agreeing sources (`ahi_summary` + EVE + trend, or 7d+ `get-comparison`, or **EVE + scan-apnea-events** on same night with reconciliation) |
| **Medium** | **60–75%** | Single strong source or partial alignment (e.g. one night O2 min; **unexplained** EVE vs scan gap) |
| **Low** | **30–50%** | Inference only, one tool, or conflicting data left unresolved |

**Not a downgrade:** skipping waveform, downsampled waveform, or `maxMinutes=2` — use EVE + scan + comparison at full High tier when they agree (see `sleephq://playbook/data-sources` § Evidence equivalency).

Table cells: `90% (High)` not just `High`.

## Section order (physician_technologist)

1. `## Technologist read` — tables and numbers only
2. `## Physician assessment` — starts with verdict, ends with **FINAL RECOMMENDATIONS**

## Physician block template

```markdown
## Physician assessment

### Verdict
**[ADEQUATE | BORDERLINE | INADEQUATE]** — one bold sentence with key numbers.

---

### FINAL RECOMMENDATIONS

#### 1. **[ACTION TITLE IN BOLD]**
**Confidence: 90% (High)**

[2–4 sentences: what to do, why, when to recheck. Cite tools in plain language.]

#### 2. **[NEXT ACTION]**
**Confidence: 70% (Medium)**

[Explanation…]

---

### Supporting findings
| Finding | Confidence | Explanation | Evidence |
| :--- | :--- | :--- | :--- |
| … | 88% (High) | … | `get-comparison` |

### Deep-night appendix
(Brief bullets per night — optional for titration reviews)

### Data completeness
(One short paragraph)
```

## Rules

- **FINAL RECOMMENDATIONS** must use `####` numbered actions with **bold titles** and **Confidence: NN% (Label)** on its own line.
- Never put recommendations only inside a small table — the callout block is mandatory.
- Findings table may stay detailed; recommendations must be visually dominant.
- Bold all AHI, pressure (cmH₂O), SpO₂ %, and leak values in narrative text.
