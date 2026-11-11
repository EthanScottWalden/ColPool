---
name: next-issue
description: >
  Find the next ColPool GitHub Issue that is eligible for work, ranked the same way every time.
  Use when picking or claiming work, when asked "what's next", or before starting a new task.
  One paged GraphQL read; makes no GitHub writes.
allowed-tools: Bash(node ${CLAUDE_SKILL_DIR}/scripts/next-issue.mjs *)
---

Run:

```
node ${CLAUDE_SKILL_DIR}/scripts/next-issue.mjs
```

Add `--json` for machine-readable output, or `--limit N` to show more of the not-yet-eligible list.

**What "eligible" means** (CLAUDE.md §2): Project Status = Ready, Human Review = Approved, Issue open, and every "blocked by" Issue closed. Ranked by Phase, then Sequence, then Priority, then Issue number.

**Then:**
- Pick the first eligible Issue unless the human named one. If the top choices share a Phase and Sequence, any of them is fine; prefer the higher priority.
- `human-task` Issues are eligible, but the human part goes in a HUMAN ACTION REQUIRED block.
- If nothing is eligible, report the top few from "next in line" and what blocks them. Don't move Issues to Ready yourself; that is the human's authorization.
- Claiming (Status → In progress) is a separate GitHub write and belongs to the lead.
