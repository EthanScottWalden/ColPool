# ColPool — Development

How to set up a machine, run the project and work inside the agent sandbox. `CLAUDE.md` §3 keeps only the main commands and points here for detail.

## 1. One-time machine setup (Windows + WSL2)

Claude Code runs in **WSL2**, where the Bash sandbox applies. On native Windows there is no sandbox and unsandboxed commands are off, so Claude Code there can't run commands. That's deliberate.

1. Install WSL2 with Ubuntu 24.04 (`wsl --install -d Ubuntu-24.04` in an admin PowerShell), then open Ubuntu.
2. Install the tools:
   ```
   sudo apt update
   sudo apt install -y git gh bubblewrap socat nodejs npm
   ```
   `bubblewrap` and `socat` are what the Claude Code sandbox uses on Linux.
3. Clone into the Linux file system, **not** `/mnt/c/...` (the sandbox can't read `/mnt`, and it's slow):
   ```
   cd ~ && git clone https://github.com/EthanScottWalden/ColPool.git
   ```
4. Sign in to GitHub as the **helper account** `ClaudePeggleMaster`, never the owner's main account:
   ```
   gh auth login        # GitHub.com, HTTPS, browser sign-in
   gh auth setup-git    # git push uses gh's credentials
   ```
5. Set the commit identity in `~/.gitconfig` to the helper account.
6. Start Claude Code from the repo root (`cd ~/ColPool && claude`). The session runs as `lead` (`.claude/settings.json`).

## 2. Build, run and test

_Filled in by the setup Issues: #11 (Android app), #12 (Functions and emulator), #54 (CI)._

| Task | Command |
|---|---|
| Android build | TBD (#11) |
| Screen screenshot tests | TBD (#11) |
| Functions unit tests (emulator) | TBD (#12) |
| Security Rules tests (emulator) | TBD (#12) |
| Start the emulator | TBD (#12) |

### Screens

Screen PRs carry images from **screenshot tests that render on the JVM**, with no device. They run in the sandbox and in CI. The owner does the real device check against the prototype screen during the staging review. The screenshot library is chosen in #11 as part of its library list (owner decision 2026-11-11).

### Dependencies

Each setup Issue (#11, #12, #54) settles its whole library list as part of its setup decision: the architect proposes the list, the owner approves it once, and it's recorded in that Issue's ADR. Adding a library outside an approved list later is L2 (owner decision 2026-11-11).

### Before CI exists

Until #54 merges, there is no CI to be green. The verifier's own run of the Issue's builds and tests, reported with commands and results, stands in for CI before merging to `dev`. When #54 merges, the owner adds its job as a required status check on the `dev-integration` ruleset.

## 3. Sandbox

Configured in `.claude/settings.json`. It applies to the main session and every subagent.

**What's blocked:**
- Reads of the home folder, except the tool folders in `sandbox.filesystem.allowRead`; reads of `/mnt` and secret files (`.env`, `local.properties`, service accounts).
- Reads of the GitHub token (`~/.config/gh`). Ordinary commands can't see it.
- Writes outside the repo and the session temp folder.
- Network hosts not in `sandbox.network.allowedDomains`.

**What runs outside the sandbox** (`sandbox.excludedCommands`): `gh *`, `git push *` and the `next-issue` script. These need the token. They still go through permission rules and the guard hook, which is the only barrier between them and the token. A compound command runs outside the sandbox only if **every** part matches, so run `gh` on its own: not after `cd ... &&`, and not piped into `head` or `grep` (use `--jq` instead). Otherwise it runs sandboxed, can't see the token, and reports "not logged into any GitHub hosts".

**The guard hook** (`.claude/hooks/gh-guard.mjs`) runs outside the sandbox before and after every Bash command. Because `gh` and `git push` run with the token, it only lets through a small set of known shapes and denies everything else:
- **Shape:** `gh` and `git push` must be one simple command. They can't be part of a compound command (`&&`, `;`, `|`, `$(...)`), and they can't use shell expansion (`$`, backticks, globs, `!`, `\`). Use `--jq` to filter and `--body-file` for long text. (A compound command would run sandboxed and fail anyway.)
- **`gh`:** only commands on the allowlist run.
  - Every agent: read commands for issues, PRs, projects, runs, workflows, labels, releases and search; `gh api` GET with no fields; a plain `gh auth status`.
  - Lead only: `issue create/edit/comment/close/reopen`, `pr create/edit/comment/merge/close/reopen/ready`, `project item-edit/item-add`, `label create`, and `gh api` writes. GraphQL goes inline (`-f query='...'`), not from a file.
  - Blocked for everyone: `alias`, `extension`, `config`, `auth` other than plain status, credential and key commands, and any repo other than `EthanScottWalden/ColPool`.
- **`git push`:** only `git push -u origin <branch>` or `git push origin <branch>`, with a plain branch name. The lead may also run `git push origin --delete <branch>`. The lead pushes any non-protected branch, implementer pushes `feature/*` and `fix/*`, docs-keeper pushes `docs/*`. Nobody pushes `dev`, `staging` or `main`. Force, refspecs, globs and other flags are denied.
- **Pacing:** at most 6 GitHub writes a minute (`GH_GUARD_MAX_WRITES_PER_MIN` raises it for one session). If the counter in `.claude/hooks/.state/` can't be saved, writes are blocked rather than allowed.
- After an Issue body or title edit, a reminder to set Human Review back to Pending.

Other tools (`curl`, `node`, `bash -c`) run inside the sandbox, where the token can't be read, so they can't act as the GitHub account.

`.claude/settings.local.json` (not committed) can set `sandbox.autoAllowBashIfSandboxed: true`, so sandboxed commands run without prompts. That's safe because sandboxed commands can't reach the token.

### Changing the allowlist

Never work around a sandbox block. When a command needs a host or folder the sandbox blocks:
1. The agent reports the exact host or path from the sandbox error to the lead.
2. The lead proposes the narrowest change (one host, not a wildcard; a tool cache, not the home folder) in the Issue's PR.
3. The owner approves the `.claude/settings.json` edit.

**Gaps to expect during setup** (not yet confirmed by running):
- Gradle, the Android SDK and the Firebase emulators write caches to `~/.gradle`, `~/.android` and `~/.cache/firebase`, which are read-only in the sandbox. Expect `sandbox.filesystem.allowWrite` entries, or project-local cache folders such as `GRADLE_USER_HOME`, in #11 and #12.
- The Firebase emulators download from Google Storage (`storage.googleapis.com`), which isn't on the allowlist. That host serves any public bucket, so #12 decides it as part of its setup decision.
