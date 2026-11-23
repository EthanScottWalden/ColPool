# Agentic workflow retrospectives

One file per week, **Sunday through Saturday** (Pacific), named by its Sunday: `YYYY-MM-DD.md`, for example `2026-11-15.md` for Sun 15 – Sat 21 November (owner decision 2026-11-16). Each covers how the agent workflow went that week. Product progress belongs in the Issues and PRs; these files record **process**: what worked, what went wrong, the owner's interventions, and the changes made because of them. The one exception is the **Presentation notes** section below, which also summarizes the week's product progress.

## Presentation notes (owner decision 2026-11-22)
The owner presents a weekly progress update for CSCI 5520 (Sunday deadline). Each weekly file ends with a **Presentation notes** section, kept current on every docs pass, so the slides become copy-over instead of reconstruction. The headings follow the course slide template.
- **Write it as you go**, not at the end of the week. Add a feature row when an Issue's PR merges into `dev`, and a decision or problem when it happens.
- **Plain language**, short enough to paste into a slide cell: about 80 characters for table cells and 1–3 sentences for boxes.
- **Time per Issue (owner decision 2026-11-23).** The lead logs agent time for each Issue it works on. It records Pacific clock times (`date`) when it claims the Issue, at each session start and end, at the dev PR, and at the merge. Time spent waiting on the owner is left out. The owner reports any human action that takes significant time, and the lead logs that number as given. The "Who + hours" cell reads `Agent ~Xh, Ethan Yh`. An Issue worked on before 2026-11-23 has no log, so its time is rebuilt from GitHub and git timestamps and marked `(est.)`, or left as `?`.
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

## Time log
| Issue | Date | Start–end (PT) | Who | What | Minutes |
|---|---|---|---|---|---|

## Presentation notes
(Order and headings match the CSCI 5520 deck; slide numbers in brackets. Only work since the last update counts.)
### What changed this week [2]
- Headline progress: <one sentence: what is possible now that wasn't last week>
- Most meaningful outcome: <concrete result: feature works, test passed>
- Biggest remaining challenge: <specific blocker, instability or unfinished capability>
- Evidence to show: <live screens, test results, Issue refs>
### Feature progress [3+] (one row per Issue worked on this week, in demo order)
| GH Issue | Feature + result | Tech / concepts | Who + hours | AI / agent tooling |
|---|---|---|---|---|
| #N | <title>. Result: <what now works> | <stack, patterns> | Agent ~Xh, Ethan Yh | Claude Code: <agents used> |
### Demo roadmap [8] (same order as the feature table)
| Order | Feature / Issue | What viewers will see | Who narrates | Proof it works |
|---|---|---|---|---|
### Agentic flow: one feature end to end [11]
- Feature: #N
- Frame: <Issue, goal, constraints, acceptance criteria>
- Delegate: <agent + task or prompt>
- Inspect: <what the owner reviewed or tested>
- Intervene: <owner decision, correction or redirection>
- Verify: <tests, PR/merge, docs, review proof>
- Documentation trail: <what the agent generated or updated; what the owner validated, corrected or added>
### Where the agent exceeded expectations [12]
- What worked surprisingly well: <…>
- What made it possible: <context, clear Issue, tests, architecture, constraints>
- Evidence: <before/after, prompt, commit, tests, time comparison>
### Where it fell short [13]
- What didn't work: <wrong assumption, missed edge case, misleading confidence>
- How it was found: <review, test, integration step>
- Recovery and what is still open: <fix, added tests, known risk>
### Owner interventions [14]
| Decision point | What the agent proposed | What the owner changed + why | Observed impact |
|---|---|---|---|
### When missing input caused damage [15]
- Where the owner stepped back too far: <…>
- Result: <rework, scope creep, broken integration, wasted effort>
- Guardrail: <checkpoint, test, smaller Issues, clearer DoD>
### Experiment: last week's process change [16] (Update #1: "first-week baseline")
- Change: <weakness seen + change made>
- Tested on: <Issues>
- What happened: <evidence>
- Keep, adjust or abandon: <decision + reason>
### Next week [17]
- Keep doing / Stop doing / Try next week (+ how to measure) / Planned evidence
### Code walkthrough [19]
- Architecture / code path: <components, files, data flow>
- Key technical decision: <choice, alternative, tradeoff>
- Quality and understanding: <tests + what was verified, CI, reviewed code/docs, gaps>
```

## Index
- [Issue #11](issue-11.md): one-off, #11 Android setup (first run) and the configuration audit.
- [2026-11-15](2026-11-15.md): #12 and #13 background; #13 to staging, #18, #15, #54 (CI), #14, #17, #16, #19, #27; address-sheet bug (Thu 19 Nov); worktree, git lock and Gradle lock friction.
