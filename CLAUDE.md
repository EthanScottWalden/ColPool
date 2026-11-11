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

- **Eligible:** Project Status = **Ready** *and* Human Review = **Approved**, not blocked by an open Issue, not already In progress. Prefer the lowest Phase, then Sequence.
- The Issue body is the spec: goal, acceptance criteria, tests expected, non-goals. Don't widen it.
- **`contract` label:** a shared definition other Issues depend on. Never change a contract inside a feature Issue. Stop and propose a contract-change Issue.
- **`human-task` label:** a person must do part of it (billing, real-world data, owner input). Agents may do the rest, and must ask (HUMAN ACTION REQUIRED, §8) for the human part.
- **Status flow:** Ready → In progress (claim it) → Agent review (PR open) → In dev (merged to dev) → In staging → Done. The human handles staging approval and Done.
- **If you edit an Issue's body or acceptance criteria**, set its Human Review back to **Pending**.
- **Pace GitHub API calls.** Prefer one bulk read over many small ones, and make writes one at a time, not in tight loops. The helper account (`ClaudePeggleMaster`) was suspended once after a burst of Issue edits. Never use or ask for the owner's main account.

## 3. Build / run / test

_Not set up yet. #11 (Android app), #12 (Functions + emulator) and #54 (CI) must fill in this section with the main commands and put the full detail in `DEVELOPMENT.md`._

- Android build: `TBD`
- Functions unit tests (emulator): `TBD`
- Security Rules tests (emulator): `TBD`
- Start the emulator: `TBD`

## 4. Definition of Done

- The Issue's acceptance criteria are met.
- The Issue's "Tests expected" exist and pass:
  - Rules changes: emulator tests.
  - Functions: unit tests against the emulator.
  - Time-based logic: fixed data, including a daylight-saving week.
  - Screens: checked on a device against the named prototype screen, with a screenshot attached to the PR.
- CI is green. No secrets or keys committed.
- User-facing text uses POLICY §11 terms and 12-hour Pacific times.
- Any decision the Issue was meant to settle is recorded in `docs/ARCHITECTURE.md` §2 (and removed from §9).

## 5. Branches and PRs

Branch model: `feature/*` → `dev` → `staging` → `main`.

- Branch from up-to-date `dev`: `feature/<issue#>-<slug>`, `fix/<issue#>-<slug>` or `docs/<slug>`.
- **All changes reach `dev` through a PR made with the GitHub CLI** (`gh pr create --base dev`). Never push directly to `dev`. The PR body says `Closes #N`. Merge with `gh pr merge` once CI passes and the work meets §4.
- **When a task is complete and ready for human review**, open a PR from `dev` into `staging` (`gh pr create --base staging --head dev`) with a short brief (§8).
- `staging` and `main` are human-only. The agent account can't push, approve or merge there, and must not try to work around that.
- No force-pushes. Don't delete `dev`, `staging` or `main`.
- If a PR includes `prompt.md`, delete `prompt.md` in that PR's final commit.

## 6. Documentation

- Record decisions an Issue settles in `docs/ARCHITECTURE.md` (§2 decision table, §9 open items).
- Don't change `SPEC.md`, `INTENT.md` or product rules in `docs/POLICY.md` without a recorded human decision.
- Keep this file short. Detailed procedures go in `DEVELOPMENT.md` or the docs above.

## 7. Escalation levels

Escalation depends on consequence, not branch. L2 and L3 apply inside `dev` too.

| Level | Behavior | ColPool examples |
|---|---|---|
| **L0** Routine | Resolve silently. | A test fails and then is fixed; lint or compiler fixes; a small refactor. |
| **L1** Noteworthy | Resolve it; mention it briefly in the summary. | A minor scope adjustment inside the Issue; a non-breaking dependency patch; tech debt found and handled. |
| **L2** Decision needed | Pause that decision path; ask (§8) with a recommendation; keep doing independent work. | An unclear product rule; a UX tradeoff; a new dependency or cost; any architecture choice, including the ones ARCHITECTURE §2 says to "decide in the issue" (UI toolkit, min SDK, Firestore layout, RC-32 lookup); interpreting policy; a prototype vs SDD difference. |
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

- **End of a task:** a short brief covering outcome, user-visible effect, how it was verified, remaining risks, and "Decision needed: none" (or a separate DECISION NEEDED block).

## 9. Agents and skills

Project agents go in `.claude/agents/` and skills in `.claude/skills/`. 