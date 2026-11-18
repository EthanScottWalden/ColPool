---
name: next-issue
description: >
  Find the next ColPool GitHub Issue that is eligible for work, ranked the same way every time, and
  list unblocked Backlog Issues for the lead to move to Ready.
  Use when picking or claiming work, when asked "what's next", or before starting a new task.
  One paged GraphQL read; the script makes no GitHub writes.
allowed-tools: Bash(node .claude/skills/next-issue/scripts/next-issue.mjs *)
---

Run it from the repo root, exactly like this and on its own (the guard hook rejects `${...}` variables, wrappers and `&&`):

```
node .claude/skills/next-issue/scripts/next-issue.mjs --json
```

Drop `--json` for a readable list, or add `--limit N` to show more of the not-yet-eligible list.

**What "eligible" means** (CLAUDE.md §2): Project Status = Ready, Human Review = Approved, Issue open, every "blocked by" Issue closed, and in the **current phase** (the lowest Phase among open Issues not yet Done; owner decision 2026-11-17). Ranked by Phase, then Sequence, then Priority, then Issue number.

**First, move unblocked Backlog Issues to Ready** (owner decision 2026-11-17). The script lists them under `promote`: open, Status = Backlog, every "blocked by" Issue closed, and in the current phase. The GitHub script won't read query files inside `.claude/`, so copy the mutation to the session temp folder once:

```
cp .claude/skills/next-issue/set-ready.graphql /tmp/claude-1000/set-ready.graphql
```

Then, for each Issue, the lead runs this on its own:

```
node .claude/hooks/github.mjs graphql-mutation --query-file /tmp/claude-1000/set-ready.graphql --var project=<promote.projectId> --var item=<itemId> --var field=<promote.statusFieldId> --var option=<promote.readyOptionId>
```

- One write at a time, at most 4 a minute (the helper account was suspended once after a burst).
- Only Backlog → Ready. Never change Human Review: Ready plus Pending is still not eligible, so the owner's approval stays the gate.
- Then run the script again; newly Ready Issues show as eligible if they are Approved.
- Report the moved Issues in one line in the next brief.

**Then:**
- Pick the first eligible Issue unless the human named one. If the top choices share a Phase and Sequence, any of them is fine; prefer the higher priority.
- `human-task` Issues are eligible, but the human part goes in a HUMAN ACTION REQUIRED block.
- If nothing is eligible, report the top few from "next in line" and what blocks them.
- Claiming (Status → In progress) is a separate GitHub write and belongs to the lead.
