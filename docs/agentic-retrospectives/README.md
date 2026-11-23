# Agentic workflow retrospectives

One file per week, **Sunday through Saturday** (Pacific), named by its Sunday: `YYYY-MM-DD.md`, for example `2026-11-15.md` for Sun 15 – Sat 21 November (owner decision 2026-11-16). Each covers how the agent workflow went that week. Product progress belongs in the Issues and PRs; these files record **process**: what worked, what went wrong, the owner's interventions, and the changes made because of them. The one exception is the **Presentation notes** section below, which also summarizes the week's product progress.

## Presentation notes (owner decision 2026-11-22)
The owner presents a weekly progress update for CSCI 5520 (Sunday deadline). Each weekly file ends with a **Presentation notes** section, kept current on every docs pass, so the slides become copy-over instead of reconstruction. The headings follow the course slide template.
- **Write it as you go**, not at the end of the week. Add a feature row when an Issue's PR merges into `dev`, and a decision or problem when it happens.
- **Plain language**, short enough to paste into a slide cell: about 80 characters for table cells and 1–3 sentences for boxes.
- **Hours are the owner's.** Agents can't measure the owner's time, so leave the hours column as `?` unless the owner gave a number. Mark any agent guess `(est.)`.
- **Walkthrough paths** must be real files, checked in the repo. Only add a line number after reading the file.
- **Evidence only**, as for the rest of the file: cite PRs, commits and decisions; mark anything unverified.

## Who writes them
- **docs-keeper** maintains them. During each docs pass (before a `dev` → `staging` PR), it updates the current week's file. On the first pass of a new week, it starts a new file from the template below.
- **The lead** supplies the session facts docs-keeper can't see: earlier chats aren't readable from the sandbox. When a chat ends mid-week, the lead adds its notes to the current file first.
- **Evidence only** (CLAUDE.md §8): cite PRs, commits, commands and results. If a section is rebuilt from git and PRs without the session transcript, say so.

## Template
```
# Agentic workflow retrospective: week of Sun DD – Sat DD Month YYYY

> Sources: <PRs, commits, sessions>. Written by <lead/docs-keeper> on <date>.

## Outcome
## Owner decisions this week
## What worked
## Problems (evidence, cost, cause)
## Changes made
## Open follow-ups

## Presentation notes
### Feature progress (one row per Issue merged this week, in demo order)
| Issue | Feature + result | Tech / concepts | Est. hours |
|---|---|---|---|
| #N | <title>. Result: <what now works> | <stack, patterns> | ? |
### What changed this week
- Headline progress: <what is possible now that wasn't last week>
- Most meaningful outcome: <concrete result>
- Biggest remaining challenge: <real blocker or next hard part>
- Evidence to show: <live screens, PR checks, Issues>
### Demo roadmap (follows the feature table)
| Order | Feature / Issue | What viewers will see | Proof it works |
|---|---|---|---|
### Agentic flow: one feature end to end
- Feature: #N. Frame / Delegate / Inspect / Intervene / Verify: <one line each>
- Documentation trail: <ADR or doc + line, PR>
### Where the agent exceeded expectations
### Where it fell short (what, how found, recovery, still open)
### Owner interventions (decision point, what the agent proposed, what the owner changed and why, impact)
### When missing input caused damage (and the guardrail)
### Experiment: last week's process change (change, where tested, result, keep/adjust/abandon)
### Next week (keep, stop, try + measure, planned evidence)
### Code walkthrough (code path with file paths, key decision, quality checks and gaps)
```

## Index
- [Issue #11](issue-11.md): one-off, #11 Android setup (first run) and the configuration audit.
- [2026-11-15](2026-11-15.md): #12 and #13 background; #13 to staging, #18, #15, #54 (CI), #14, #17, #16, #19, #27; address-sheet bug (Thu 19 Nov); worktree, git lock and Gradle lock friction.
