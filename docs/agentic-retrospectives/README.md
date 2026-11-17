# Agentic workflow retrospectives

One file per ISO week (Monday–Sunday, Pacific): `YYYY-Www.md`, for example `2026-W47.md`. Each covers how the agent workflow went that week. Product progress belongs in the Issues and PRs; these files record **process**: what worked, what went wrong, the owner's interventions, and the changes made because of them.

## Who writes them
- **docs-keeper** maintains them. During each docs pass (before a `dev` → `staging` PR), it updates the current week's file. On the first pass of a new week, it starts a new file from the template below.
- **The lead** supplies the session facts docs-keeper can't see: earlier chats aren't readable from the sandbox. When a chat ends mid-week, the lead adds its notes to the current file first.
- **Evidence only** (CLAUDE.md §8): cite PRs, commits, commands and results. If a section is rebuilt from git and PRs without the session transcript, say so.

## Template
```
# Agentic workflow retrospective: YYYY-Www (Mon DD – Sun DD Month)

> Sources: <PRs, commits, sessions>. Written by <lead/docs-keeper> on <date>.

## Outcome
## Owner decisions this week
## What worked
## Problems (evidence, cost, cause)
## Changes made
## Open follow-ups
```

## Index
- [2026-W46](2026-W46.md): #11 Android setup (first run) and the configuration audit; #12 and #13 summary.
- [2026-W47](2026-W47.md): #13 to staging, #18, #15, #54 (CI), #14, #17, #16; worktree, git lock and Gradle lock friction.
