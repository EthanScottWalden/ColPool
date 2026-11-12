# ColPool — Claude operating guide

ColPool is a native Android app (Kotlin) on Firebase (Auth, Firestore, Cloud Functions in TypeScript) that lets California Baptist University commuters host and join recurring round-trip carpools ("ride series"). Core rule: **the client reads, the backend writes.**

This file is the operating guide for every session. It points to the detailed docs; it does not repeat them.

## 1. Sources of truth

| Question | Read | Authority |
|---|---|---|
| Product behavior, rules (RC-##, US-##) | `SPEC.md` (SDD v0.5) | Wins on product |
| Sequencing, scope guardrails, agent guardrails | `INTENT.md` §1.3, §4, §6.3 | Wins on process and guardrails |
| Stack, components, boundaries, invariants | `docs/ARCHITECTURE.md` | Summary; fixed if it disagrees with the two above |
| Domain, privacy and safety rules, non-goals | `docs/POLICY.md` | Summary; fixed if it disagrees with the two above |
| Phase direction | `docs/ROADMAP.md` | High level only |
| What to build now | The assigned GitHub Issue + [Project](https://github.com/users/EthanScottWalden/projects/1) | Task intent, acceptance criteria, order |

- Read the Issue first, then only the doc sections it cites or that its change touches.
- The prototype (`docs/issue-assets/prototype/`) is a visual reference, not an authority. SPEC wins.
- If sources conflict, or a task needs a rule that isn't written down: **stop and flag it** (L2 or L3, §7). Never pick an answer.

## 2. Picking up work

- **Eligible:** Project Status = **Ready** *and* Human Review = **Approved**, not blocked by an open Issue, not already In progress. Prefer the lowest Phase, then Sequence. Use the `next-issue` skill to list them.
- The Issue body is the spec: goal, acceptance criteria, tests expected, non-goals. Don't widen it.
- **Stale Issue template text:** the "Tests expected → Screens: a check on an Android Emulator device" line and the "Project fields" snapshot in Issue bodies predate later decisions. §4 and the live Project win over them. That isn't a conflict to flag.
- **`contract` label:** a shared definition other Issues depend on. Never change a contract inside a feature Issue. Stop and propose a contract-change Issue.
- **`human-task` label:** a person must do part of it (billing, real-world data, owner input). Agents may do the rest, and must ask (HUMAN ACTION REQUIRED, §8) for the human part.
- **Status flow:** Ready → In progress (claim it) → Agent review (PR open) → In dev (merged to dev) → In staging → Done. The human handles staging approval and Done.
- **Setup Issues (lead):** run the environment preflight (`DEVELOPMENT.md` §2 "Setup preflight") before starting the implementer, and send the human **one** HUMAN ACTION REQUIRED with commands you have verified.
- **If you edit an Issue's body or acceptance criteria**, set its Human Review back to **Pending**.
- **Pace GitHub API calls.** Prefer one bulk read over many small ones, and make writes one at a time, not in tight loops. The helper account (`ClaudePeggleMaster`) was suspended once after a burst of Issue edits. Never use or ask for the owner's main account. A hook enforces this: more than 6 GitHub writes a minute, or a write from a subagent (other than `push` of its own `feature/*`, `fix/*` or `docs/*` branch), is blocked. Raise the limit for one session with `GH_GUARD_MAX_WRITES_PER_MIN`.

## 3. Build / run / test

_Android is set up (#11). #12 (Functions + emulator) and #54 (CI) fill in the rest, with the full detail in `DEVELOPMENT.md` §2._

Agents run Gradle through `android/scripts/sandbox-gradlew.sh <tasks>` (sandbox proxy and temp dir); humans use `./gradlew`.

- Android build: `cd android && ./gradlew assembleDebug` (details: `DEVELOPMENT.md` §2)
- Functions unit tests (emulator): `TBD`
- Security Rules tests (emulator): `TBD`
- Start the emulator: `TBD`

## 4. Definition of Done

- The Issue's acceptance criteria are met.
- The Issue's "Tests expected" exist and pass:
  - Rules changes: emulator tests.
  - Functions: unit tests against the emulator.
  - Time-based logic: fixed data, including a daylight-saving week.
  - Screens: screenshot-test images of the screen attached to the PR; the human checks it on a device against the named prototype screen during the staging review (`DEVELOPMENT.md` §2).
- CI is green. Until #54 adds CI, the verifier's own run of the builds and tests stands in for it (`DEVELOPMENT.md` §2). No secrets or keys committed.
- User-facing text uses POLICY §11 terms and 12-hour Pacific times.
- Any decision the Issue was meant to settle is recorded (§6).

## 5. Branches and PRs

Branch model: `feature/*` → `dev` → `staging` → `main`.

- Branch from up-to-date `dev`: `feature/<issue#>-<slug>`, `fix/<issue#>-<slug>` or `docs/<slug>`.
- **All changes reach `dev` through a PR** made with the GitHub script (`node .claude/hooks/github.mjs pr-create --base dev ...`). Never push directly to `dev`. The PR body says `Closes #N`. Merge with `node .claude/hooks/github.mjs pr-merge <n>` once CI passes and the work meets §4.
- **When a task is complete and ready for human review**, open a PR from `dev` into `staging` (`node .claude/hooks/github.mjs pr-create --base staging --head dev ...`) with a short brief (§8).
- `staging` and `main` are human-only. The agent account can't push, approve or merge there, and must not try to work around that.
- After a `feature/*`, `fix/*` or `docs/*` PR merges into `dev`, the lead deletes its branch on GitHub (`node .claude/hooks/github.mjs branch-delete <branch>`, owner decision 2026-11-12).
- No force-pushes. Don't delete `dev`, `staging` or `main`.
- If a PR includes `prompt.md`, delete `prompt.md` in that PR's final commit.

## 6. Documentation

- **Recording a decision an Issue settles:** put the detail in the ADR or doc the Issue names (e.g. `docs/adr/0001-android-setup.md`, `docs/data-model.md`). Then add a one-line value plus a link in `docs/ARCHITECTURE.md` §2, and remove the item from §9.
- Don't change product rules in `docs/POLICY.md` without a recorded human decision.
- `SPEC.md` and `INTENT.md` change only with approval from both the lead and the human.
- Keep this file short. Detailed procedures go in `DEVELOPMENT.md` or the docs above.

## 7. Escalation levels

Escalation depends on consequence, not branch. L2 and L3 apply inside `dev` too.

| Level | Behavior | ColPool examples |
|---|---|---|
| **L0** Routine | Resolve silently. | A test fails and then is fixed; lint or compiler fixes; a small refactor. |
| **L1** Noteworthy | Resolve it; mention it briefly in the summary. | A minor scope adjustment inside the Issue; a non-breaking dependency patch; tech debt found and handled. |
| **L2** Decision needed | Pause that decision path; ask (§8) with a recommendation; keep doing independent work. | An unclear product rule; a UX tradeoff; a new dependency outside the setup Issue's approved list, or a new cost (`DEVELOPMENT.md` §2); any architecture choice, including the ones ARCHITECTURE §2 says to "decide in the issue" (UI toolkit, min SDK, Firestore layout, RC-32 lookup); interpreting policy; a prototype vs SDD difference. |
| **L3** Stop the line | Don't do the risky action until explicitly resolved. | Changing Security Rules or the privacy/consent model beyond the Issue; changing a contract; deleting data or records; secrets or keys; Firestore region; billing; deploying to the real Firebase project (process not decided yet); anything in POLICY §13 non-goals; a contradiction with SPEC; irreversible git or GitHub actions. |

## 8. Talking to the human

- **Questions:** use the structured choice UI. Put the recommended option first, marked "(Recommended)". Give each option a concrete practical effect. Never bury a question in a status update. If the UI isn't available, use this format:

  ```
  DECISION NEEDED: <short title>
  Why this matters: <1-3 sentences in product language>
  A. <recommended> (Recommended): Practical effect: … Tradeoff: …
  B. <alternative 1>: Practical effect: … Tradeoff: …
  C. <alternative 2>: ...
  Reply with A/B/..., or tell me a different direction.
  ```

- **Missing permission:** say it once, clearly, then keep doing independent work:

  ```
  HUMAN ACTION REQUIRED
  Action: <exact command, UI path or steps>
  Why I can't: <permission or credential missing>
  Why it's needed / Benefit: <…>
  Expected result: <what you should see>
  How I'll verify: <check I'll run before resuming>
  ```

- **Evidence:** in GitHub writes and briefs, cite only commits, commands and results you produced or read in this session. If you didn't check something, say so or leave it out.
- **End of a task:** a short brief covering outcome, user-visible effect, how it was verified, remaining risks, and "Decision needed: none" (or a separate DECISION NEEDED block).

## 9. Agents and skills

Project agents go in `.claude/agents/` and skills in `.claude/skills/`.

The main session runs as `lead` (`.claude/settings.json`). It is the only agent that talks to the human, spawns other agents, or writes to GitHub (PRs, merges, Issue and Project changes). Subagents may only `push` their own branches (see Guardrails).

| Agent | Use for | Edits |
|---|---|---|
| `product-domain` | Unclear Issue intent; RC/US rules, privacy, consent, wording; prototype vs SPEC | No |
| `architect` | Data model, contracts, new functions, transactions, dependencies, "decide in issue" choices | No |
| `implementer` | Writing code, tests and decision records once the plan is settled | Code, tests, docs the Issue names |
| `verifier` | UX, QA and security review of a change | No |
| `docs-keeper` | Doc consistency and drift | Docs (`SPEC.md`/`INTENT.md` only with lead + human approval) |

**Skills:**
- `next-issue` lists eligible Issues in rank order (read-only).
- Doc-link skills, each with official links plus ColPool constraints: `firebase-auth-docs`, `cloud-functions-docs`, `firestore-docs`, `maps-routes-docs`, `maps-places-docs`, `kotlin-docs`. Use the matching one before relying on memory for an external API.
- Preloaded: all six in `implementer` and `architect`; Auth, Functions and Firestore in `verifier`.
**Guardrails** (`.claude/settings.json`):
- Deny rules for deploys, Firebase deletes, force-pushes and direct pushes to `dev`/`staging`/`main`.
- An allowlist for read-only `git` commands and for the GitHub script.
- **GitHub goes only through `node .claude/hooks/github.mjs <op>`** (run it with no op for the list). It is the only command outside the sandbox with the token, runs no shell, and allows only named operations (`DEVELOPMENT.md` §3). Raw `gh` and `git push` run sandboxed without the token, and the hook denies them.
- `.claude/hooks/gh-guard.mjs`: who may run which operation (writes lead-only; `push` also implementer `feature/*` and `fix/*`, docs-keeper `docs/*`); the script must be called exactly, on its own; write pacing; a Human Review reminder after Issue edits.

GitHub rulesets remain the real guard for `dev`/`staging`/`main`. `.github/CODEOWNERS` makes a PR into `dev` that changes `SPEC.md`, `INTENT.md`, `CLAUDE.md`, `docs/POLICY.md`, `.claude/` or `.github/` wait for the owner's review.

**Sandbox:** run Claude Code in WSL2, where the sandbox in `.claude/settings.json` applies (see `DEVELOPMENT.md`). On native Windows there's no sandbox, and unsandboxed commands are off, so Claude Code there can't run commands. That's deliberate. Never work around a sandbox block; propose an allowlist change in a PR.
