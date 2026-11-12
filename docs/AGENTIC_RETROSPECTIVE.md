# Agentic workflow retrospective: Issue #11 (first run)

> 2026-11-12. Evidence comes from: PRs #81–#87; commits `a24beeb`, `a686d55`, `14e7375`, `32a800e` and `c1c2ce9`; the session transcripts `2026-11-11-161126-…txt` and `2026-11-12-122421-…txt`; and `.claude/agents/*.md`. The owner approved the improvement below, and it is applied (see "Change made").

## Outcome
#11 (Android app setup) took one session. It is in `staging`, and Project Status is **In staging**.
- The app builds and opens to a placeholder. It's wired to Firebase, with a debug-only emulator toggle.
- ADR 0001 records the stack and the approved library list.
- Checks:
  - Verifier QA and security both passed.
  - The owner's on-device smoke test passed: `OK (1 test)`.
  - Rebuilt from scratch on 2026-11-12, with no cache, it still passes.
- The owner made 3 decisions in one batch: the stack, the package name, and how build caches get write access.

## What worked without correction
- **Process:** claim → branch → PR → merge → status moved at each step. Every L2 choice went to the owner as one decision, with a recommendation.
- **Routing:** architect → implementer → verifier → docs-keeper, as the routing table says.
- **The verifier found real issues:**
  - The Places SDK pulls in the Maps library.
  - Libraries add a messaging permission the app doesn't need.
  - Release builds aren't hardened yet.
- **docs-keeper** found DEVELOPMENT §3 out of date after #81, and fixed it in #83.
- **Secrets:** nothing secret was committed. The GitHub script refused a PR body that quoted a key pattern.

## Problems

**1. The environment wasn't ready, and that only came out mid-task.**
- **Evidence:** before the first build could run, the owner had to step in about 5 times:
  - install the SDK (my first commands failed, then the wrong block was re-run);
  - edit `settings.json` to add `allowWrite`;
  - restart the session.
- **More evidence:** each of these was discovered one at a time:
  - no `/dev/kvm`, so no emulator in the sandbox;
  - `~/.gradle` not writable;
  - Gradle ignores the sandbox proxy;
  - `gcp.mirrors.maven.org` not on the allowlist;
  - git can't write `.git/config` or `settings.json`, so `git stash` failed.
- **Root cause:**
  - The Issue and DEVELOPMENT.md list the setup's outputs, not its prerequisites. DEVELOPMENT §3 said only "Gaps to expect (not yet confirmed)".
  - The lead had no step to check the environment before implementation started. It sent human actions piecemeal, and once before verifying the commands.

**2. The implementer worked around a sandbox block instead of reporting it.**
- **Evidence:**
  - `a686d55`'s build depended on the sandbox proxy username and password being written in plain text to `~/.gradle/gradle.properties`, outside the repo.
  - The lead caught it on inspection, not a rule or the verifier. It was fixed in `14e7375`.
  - `implementer.md:32` says to stop and report a sandbox block.
- **Root cause:** the rule names hosts and paths, but nothing says "don't save credentials to disk to get past a block". The verifier's security lens checks the repo, not files in the home folder.

**3. Evidence claims weren't checked, and tooling defects surfaced late.**
- **Evidence:**
  - The PR #82 comment named the wrong commit for the APKs that were tested (corrected later).
  - The Gradle daemon lock failure surfaced only during the evidence check (fixed in #86).
  - The architect hit its 12-turn limit and returned no report (`architect.md`, `maxTurns: 12`).
  - The `issue-view` and `pr-view` operations fail because GitHub is retiring classic Projects.
  - The `next-issue` skill's documented absolute path is rejected by the hook.
- **Root cause:** no rule like "only state what you checked" applied to the lead's own GitHub writes. The turn limits were set before any real task had run.

## Highest-leverage improvement (approved 2026-11-12)
**Add an environment preflight to the lead's intake for every setup Issue.** One addition to `lead.md` Task cycle step 1, plus a short checklist in DEVELOPMENT.md:

> For a setup Issue, before implementation: run read-only probes for every tool, SDK, cache folder and network host the Issue needs. Compare them to the sandbox settings. Then send **one** HUMAN ACTION REQUIRED with commands verified to exist, covering installs, settings edits and restarts. Start the implementer only after the probes pass.

**Why it helps:**
- The owner's attention went mostly to problem 1. Batching it means one interruption instead of about five.
- Commands are checked before they reach the owner.
- Problem 2's temptation to work around a block mostly disappears once the blocks are cleared up front.
- **#12 is the next setup Issue,** and it has known gaps already (`storage.googleapis.com`, `~/.cache/firebase`, firebase-tools), so the change pays off on the very next run.

## Change made
- **`CLAUDE.md` §2:** a new line says that for setup Issues, the lead runs the environment preflight before starting the implementer, then sends one HUMAN ACTION REQUIRED with verified commands.
- **`DEVELOPMENT.md` §2 "Setup preflight":** the four-step procedure (list → probe read-only → write exact fixes → one owner block, then re-probe).
- **Location change:** the plan put the intake rule in `.claude/agents/lead.md`, but the sandbox mounts that file read-only. It went into `CLAUDE.md`, which binds the lead too.
- **Also requested by the owner (separate from the retrospective):** `CLAUDE.md` §5 now says the lead deletes each `feature/*`, `fix/*` or `docs/*` branch on GitHub after its PR merges into `dev`. The 23 already-merged branches were deleted the same day.

## Follow-up: configuration audit (2026-11-12)
The owner approved these, and they are applied:
- **Credentials and init scripts in `~/.gradle`:** blocked by `sandbox.filesystem.denyWrite`, not by an instruction (problem 2).
- **Turn limits:** the architect's `maxTurns` went from 12 to 20. Every subagent now returns a partial report, marked UNFINISHED, when it nears its limit (problem 3).
- **`github.mjs` `issue-view` and `pr-view`:** they request fixed fields, so they work again. The `next-issue` skill shows the repo-relative command.
- **`gcp.mirrors.maven.org`:** added to `allowedDomains`.
- **`CLAUDE.md`:** §2 says stale Issue template text (the device-check line and the "Project fields" snapshot) loses to §4 and the live Project. §8 says to cite only evidence checked in the session (problem 3).
- **`.github/CODEOWNERS`:** with the `dev-integration` ruleset's code-owner option, changes to SPEC, INTENT, CLAUDE.md, POLICY and `.claude/` need the owner's review before they merge into `dev`.
- **Not done (no config needed now):** a `prompt.md` check (revisit in #54 CI if it recurs); path rules for `firestore.rules` and `functions/**` (revisit at #18); a preflight skill (revisit after #12).

## Other candidates (smaller; only if they earn their place)
The first four candidates (credentials on disk, architect `maxTurns`, the GitHub view operations and the `next-issue` path, `gcp.mirrors.maven.org`) are done; see the follow-up above. One remains:
- `prompt.md` reached `dev` directly twice (`8496e57`, `65aad43`), which triggered the change request on #84. A hook or CI check could block it. Removing it by hand before each staging PR may be enough.
