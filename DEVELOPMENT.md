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
   gh auth setup-git    # git push (via the GitHub script) uses gh's credentials
   ```
5. Set the commit identity in `~/.gitconfig` to the helper account.
6. Start Claude Code from the repo root (`cd ~/ColPool && claude`). The session runs as `lead` (`.claude/settings.json`).
7. **Android SDK in WSL (Linux).** The sandbox builds the Android app with a Linux SDK (JDK 21 is already on the machine):
   ```
   mkdir -p ~/Android/Sdk/cmdline-tools && cd ~/Android/Sdk/cmdline-tools
   curl -fsSLO https://dl.google.com/android/repository/commandlinetools-linux-16111833_latest.zip
   unzip commandlinetools-linux-16111833_latest.zip && mv cmdline-tools latest
   echo 'export ANDROID_HOME=$HOME/Android/Sdk' >> ~/.profile && export ANDROID_HOME=$HOME/Android/Sdk
   ~/Android/Sdk/cmdline-tools/latest/bin/sdkmanager --no_metrics --licenses
   ~/Android/Sdk/cmdline-tools/latest/bin/sdkmanager --no_metrics "platform-tools" "platforms;android-37.0" "build-tools;36.0.0" "cmdline-tools;latest"
   ```
   - `sdkmanager` is now deprecated in favour of `android sdk` (same packages). Metrics can be turned off with `--no-metrics`.
   - Do **not** run `android init`: it installs unreviewed agent tooling.
   - Put the real Places key in `android/local.properties` (`PLACES_API_KEY=...`, never committed). Gradle finds the SDK from `ANDROID_HOME`, so no `sdk.dir` is needed. The sandbox can't read `local.properties`, so the build uses the committed placeholder there.
   - **Gradle in the sandbox.** Gradle's JVMs ignore the sandbox's proxy environment variables, so builds there need the proxy in `~/.gradle/gradle.properties` (`systemProp.http(s).proxyHost/Port/User/Password`, copied from `$HTTPS_PROXY`), plus `-Djdk.http.auth.tunneling.disabledSchemes=` in `GRADLE_OPTS` and in `-Dorg.gradle.jvmargs`, and `-Djava.io.tmpdir=$TMPDIR` in `-Dorg.gradle.jvmargs` (`/tmp` is read-only). Keep these in a personal wrapper script, never in the repo. The first build also needs the hosts `services.gradle.org`, `plugins.gradle.org`, `dl.google.com`, `repo.maven.apache.org` and `gcp.mirrors.maven.org` reachable.
   - Device checks and the instrumented smoke test use the owner's **Windows Android Studio emulator**, not WSL (no `/dev/kvm`).

## 2. Build, run and test

_Filled in by the setup Issues: #11 (Android app), #12 (Functions and emulator), #54 (CI)._

| Task | Command |
|---|---|
| Android build | `cd android && ./gradlew assembleDebug` (debug builds target the Emulator Suite by default; `-Pcolpool.useEmulators=false` for real Firebase, which needs `google-services.json`) |
| Android JVM tests (Robolectric smoke test) | `cd android && ./gradlew testDebugUnitTest` |
| Screen screenshot tests (Roborazzi) | `cd android && ./gradlew verifyRoborazziDebug`; refresh goldens with `recordRoborazziDebug` and commit the images (`app/src/test/screenshots/`) |
| Android instrumented smoke test (owner-run) | Compiles with `./gradlew assembleDebugAndroidTest`. The owner runs `./gradlew connectedDebugAndroidTest` on the Windows emulator. CI runs it after #54. |
| Functions unit tests (emulator) | TBD (#12) |
| Security Rules tests (emulator) | TBD (#12) |
| Start the emulator | TBD (#12) |

### Screens

Screen PRs carry images from **screenshot tests that render on the JVM**, with no device. They run in the sandbox and in CI. The owner does the real device check against the prototype screen during the staging review. The library is Roborazzi on Robolectric ([ADR 0001](docs/adr/0001-android-setup.md), owner decision 2026-11-11).

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

**What runs outside the sandbox** (`sandbox.excludedCommands`): only the GitHub script `node .claude/hooks/github.mjs` and the `next-issue` script. They are the only commands that can use the token. Both live in folders sandboxed commands can't write, and both call `/usr/bin/gh` and `/usr/bin/git` without a shell and with a minimal environment (`PATH`, `HOME`, `LANG`), so nothing from the Claude Code process, such as tokens, `TMPDIR` or `NODE_OPTIONS`, reaches them.

**The GitHub script** (`.claude/hooks/github.mjs`) is the only way agents reach GitHub. Run it with no operation for the full list.
- **Reads (any agent):** `auth-status`, `issue-view`, `issue-list`, `pr-view`, `pr-list`, `pr-checks`, `pr-diff`, `api-get` (paths under `repos/EthanScottWalden/ColPool/` or `users/EthanScottWalden/`).
- **Lead-only read:** `graphql-query` (exactly one query, no mutations or fragments). It can read anything the helper account can see, not only ColPool, so subagents can't run it (owner decision 2026-11-11).
- **Writes (lead):** issue create/edit/comment/close/reopen; `pr-create` (into `dev`, or `dev` into `staging`); PR edit/comment/close; `pr-merge` (PRs into `dev` only, merge commit, never `--admin`); `graphql-mutation` (Project field and Issue-link mutations only); `branch-delete` (never `dev`/`staging`/`main`).
- **`push <branch>`:** the lead (any branch except `dev`/`staging`/`main`), implementer (`feature/*`, `fix/*`), docs-keeper (`docs/*`). It runs `git push -u origin refs/heads/<branch>:refs/heads/<branch>`. No force, refspecs or globs.
- Text goes in files (`--body-file`, `--query-file`). Files must be `.md`, `.txt`, `.graphql` or `.json`, in the repo or under `/tmp/claude-<uid>/`, and not a dotfile or inside a dot-folder (so not `.git`, `.claude` or `.env`). `TMPDIR` isn't trusted. A symlink as the file is refused, and the file is checked after it's opened, so it can't be swapped. Secret-looking names, and contents that look like a token, API key, `Bearer` header or private key, are refused.
- GraphQL is checked with a real lexer: comments and strings can't hide a mutation, `--var` can't replace `query`, `operationName` or `variables`, and `graphql-mutation` allows only the listed Project and Issue-link mutations. Mutations can't contain backslash escapes (pass values with `--var`). It refuses to set the Project's **Human Review** field to Approved (only the human approves, CLAUDE.md §2); if it can't read that field's options, it refuses Project field changes.

**The guard hook** (`.claude/hooks/gh-guard.mjs`) runs outside the sandbox before and after every Bash command:
- The script must be called exactly as `node .claude/hooks/github.mjs <op> ...`, on its own, and the next-issue script exactly as `node .claude/skills/next-issue/scripts/next-issue.mjs [--json] [--limit N]`. Any command that mentions either script counts as a call unless its first word is a plain reader such as `git`, `grep` or `cat`. Wrappers (`env`, `env -u`, `timeout`, `nice`, `time`, `stdbuf`, `nohup`), `NODE_OPTIONS`, node flags, pipes, `&&`, `$(...)` and globs are denied. Claude Code strips some of these wrappers when it matches `excludedCommands`, so they would otherwise run outside the sandbox.
- Reads: any agent, except `graphql-query` (lead only). Writes: the lead. `push`: the branch rules above.
- Raw `gh` and `git push` are denied with a pointer to the script. Outside it they would run sandboxed without the token anyway.
- **Pacing:** at most 6 writes a minute (`GH_GUARD_MAX_WRITES_PER_MIN` raises it for one session). If the counter in `.claude/hooks/.state/` can't be saved, writes are blocked rather than allowed.
- After an Issue body or title edit, a reminder to set Human Review back to Pending.

Every other command (`curl`, `node`, `bash -c`, `gh` itself) runs inside the sandbox, where the token can't be read.

**Token scopes:** the helper account's token needs `project` for the next-issue script and for Project field updates (`gh auth refresh -s project`, run by the owner outside Claude Code).

`.claude/settings.local.json` (not committed) can set `sandbox.autoAllowBashIfSandboxed: true`, so sandboxed commands run without prompts. That's safe because sandboxed commands can't reach the token.

### Changing the allowlist

Never work around a sandbox block. When a command needs a host or folder the sandbox blocks:
1. The agent reports the exact host or path from the sandbox error to the lead.
2. The lead proposes the narrowest change (one host, not a wildcard; a tool cache, not the home folder) in the Issue's PR.
3. The owner approves the `.claude/settings.json` edit.

**Gaps to expect during setup** (not yet confirmed by running):
- Gradle, the Android SDK and the Firebase emulators write caches to `~/.gradle`, `~/.android` and `~/.cache/firebase`, which are read-only in the sandbox. Expect `sandbox.filesystem.allowWrite` entries, or project-local cache folders such as `GRADLE_USER_HOME`, in #11 and #12.
- The Firebase emulators download from Google Storage (`storage.googleapis.com`), which isn't on the allowlist. That host serves any public bucket, so #12 decides it as part of its setup decision.
