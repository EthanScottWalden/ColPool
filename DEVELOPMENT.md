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
   - Put the real Places key in `local.properties` at the **repo root** (`PLACES_API_KEY=...`, never committed; it used to be `android/local.properties`, so move your old file once per clone). Gradle finds the SDK from `ANDROID_HOME`, so no `sdk.dir` is needed. The sandbox can't read `local.properties`, so the build uses the committed placeholder there.
   - **Gradle in the sandbox.** Gradle's JVMs ignore the sandbox's proxy settings, and `/tmp` is read-only there. Agents run Gradle through `android/scripts/sandbox-gradlew.sh <tasks>` (it runs the repo-root `./gradlew`): it sets `ANDROID_HOME`, the temp dir and the proxy for that one command only, writes no files and prints no credentials. It always runs with `--no-daemon`: a daemon left over from an earlier sandboxed command holds the cache lock and keeps stale proxy credentials. It uses its own Gradle home per checkout, `<checkout>/.cache/gradle-agent` (gitignored), never `~/.gradle`: Gradle releases cache locks between processes with a localhost message that can't cross the sandbox's private network, so an owner daemon, Android Studio or an orphaned build would otherwise block agent builds (owner decision 2026-11-16). The first agent build in a checkout downloads dependencies again. Humans use plain `./gradlew`. Never put proxy settings in `~/.gradle/gradle.properties`: it would break normal builds. The sandbox enforces this: `~/.gradle/gradle.properties`, `~/.gradle/init.gradle(.kts)` and `~/.gradle/init.d` are in `sandbox.filesystem.denyWrite`, so agents can't save credentials there or plant init scripts that would run in the owner's unsandboxed builds. Inside the sandbox they appear empty. The first sandboxed build needs `services.gradle.org`, `plugins.gradle.org`, `dl.google.com`, `repo.maven.apache.org` and `gcp.mirrors.maven.org` reachable.
   - Device checks and the instrumented smoke test use the owner's **Windows Android Studio emulator**, not WSL (no `/dev/kvm`).

## 2. Build, run and test

_Set up by #11 (Android app), #12 (Functions and emulator) and #54 (CI)._

| Task | Command |
|---|---|
| Android build | `./gradlew assembleDebug` from the repo root, which is the Gradle root since 2026-11-17 (agents: `android/scripts/sandbox-gradlew.sh assembleDebug`; same for every Gradle task below) (debug builds target the Emulator Suite by default; `-Pcolpool.useEmulators=false` for real Firebase, which needs `google-services.json`) |
| Android JVM tests (Robolectric smoke test) | `./gradlew testDebugUnitTest` |
| Screen screenshot tests (Roborazzi) | `./gradlew verifyRoborazziDebug`; refresh goldens with `recordRoborazziDebug` and commit the images (`android/app/src/test/screenshots/`) |
| Android instrumented smoke test (owner-run) | Compiles with `./gradlew assembleDebugAndroidTest`. The owner runs `./gradlew connectedDebugAndroidTest` on the Windows emulator. CI runs it (job `android-instrumented`) on PRs into `staging` only; not a required check (owner decision 2026-11-16, ADR 0001). |
| Functions unit tests (emulator) | `cd functions && npm test` runs the function unit tests (handlers called with `.run`) and the Rules tests against the Auth and Firestore emulators; agents can run it (install with `functions/scripts/sandbox-npm.sh ci`). Needs Node >=24.12 and JDK 21. `cd functions && npm run test:e2e` calls functions through the Functions emulator; humans and CI only, it can't run in the agent sandbox. |
| Security Rules tests (emulator) | Same command (`cd functions && npm test`); rules tests live in `functions/test/rules/`, function unit tests in `functions/test/functions/`. |
| Start the emulator | `cd functions && npm run emulators` (empty), or `npm run emulators:dev` (also seeds reference data and test accounts; see "Run the app from Android Studio"). The Firestore emulator jar is downloaded once by the owner, outside the sandbox, into `.cache/firebase/emulators/cloud-firestore-emulator-v1.22.0.jar`; check its SHA-256 `9b6498b7f62714d67f48f59b3818883cd682dbcd46b9f59511de81c97bb5166c` (firebase-tools does not check cached jars). Repeat whenever the firebase-tools pin changes. See [ADR 0003](docs/adr/0003-functions-emulator-setup.md). |

### Run the app from Android Studio (Windows)

The emulators run in WSL2; Android Studio and the Android emulator run on Windows from a separate clone. Debug builds already target the emulators (`10.0.2.2`, project `demo-colpool`; ADR 0001).

1. **One-time, per Windows clone.** Pull the branch. Since 2026-11-17 the Gradle root is the **repo root**: move your old `android\local.properties` (Places key, `sdk.dir`) to `ColPool\local.properties`, then in Android Studio use File > Open on the whole `ColPool` folder (not `android`) and let Gradle sync. The module list shows `app`. You can delete the old `.idea` folder under `android\` if Studio still lists the old project.
2. **Run.** Pick **app** and press Run on an Android emulator. Before a debug build, the Gradle task `ensureFirebaseEmulators` (`android/app/build.gradle.kts`) checks `localhost:9099`:
   - If it answers, the emulators are already up and nothing else happens.
   - If not, it opens a **minimized console window titled "ColPool emulators"** (in the taskbar) that runs `npm run emulators:dev` in WSL (`bash -lc`, loads nvm, `cd ~/ColPool/functions`): build, start Auth, Firestore and Functions, seed reference data and the test accounts. The build then waits (up to 240 seconds, polling every 2 seconds) until `dev.staff@calbaptist.edu` can sign in, which means seeding is done, and continues to install the app.
   - **To stop the emulators, close that window.** If the next start says a port is in use, run `wsl pkill -f firebase` in a Windows terminal. Data isn't persisted, so every start is clean and seeded.
   - If it times out, the build fails with a message. In WSL run `cd ~/ColPool/functions && npm run emulators:dev` and read the output (or open the console window).
3. **When it does nothing.** The task only acts on Windows, with `colpool.useEmulators=true` and `colpool.autoStartEmulators=true` (default), and no `CI` environment variable. On Linux, in CI and in the agent sandbox it is skipped. Turn it off with `-Pcolpool.autoStartEmulators=false` or in `%USERPROFILE%\.gradle\gradle.properties`. If your WSL checkout isn't `~/ColPool`, set `colpool.wslCheckout=~/path` there (no spaces). Node must come from nvm (`~/.nvm/nvm.sh`) in WSL.
4. **Another Windows account.** WSL distros are per Windows account, so in a second Windows profile `wsl.exe` opens that profile's own WSL, and the "ColPool emulators" window fails with `No such file or directory` (no `~/ColPool` there). `colpool.wslCheckout` can't reach another account's WSL. Do the one-time setup in "Second Windows profile" below.

### Second Windows profile

One-time setup so Run works from a Windows account other than the one that owns the main WSL checkout. Do every step signed in to Windows as that account.

1. **WSL with Ubuntu** (admin PowerShell), then open Ubuntu and create a Linux user:
   ```
   wsl --install -d Ubuntu-24.04
   ```
   Then make it the **default** distro. The build runs plain `wsl.exe`, which opens the default, and Docker Desktop often registers itself as the default (`docker-desktop`, no `~/ColPool`). In PowerShell, `wsl -l -v` marks the default with `*`:
   ```
   wsl --set-default Ubuntu-24.04
   ```
2. **git, curl and Java 21** (the emulators run on the JVM):
   ```
   sudo apt update
   sudo apt install -y git curl openjdk-21-jre-headless
   ```
3. **nvm and Node 24.** The auto-start loads Node from `~/.nvm/nvm.sh`:
   ```
   curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.3/install.sh | bash
   source ~/.nvm/nvm.sh
   nvm install 24 && nvm alias default 24
   ```
4. **Clone to `~/ColPool`** (Linux file system, not `/mnt/c`) and install:
   ```
   cd ~ && git clone https://github.com/EthanScottWalden/ColPool.git
   cd ~/ColPool/functions && npm ci
   ```
5. **Firestore emulator jar**, same file and SHA-256 as CI (`.github/workflows/ci.yml`). The last command must print `OK`:
   ```
   cd ~/ColPool
   mkdir -p .cache/firebase/emulators
   curl -fL -o .cache/firebase/emulators/cloud-firestore-emulator-v1.22.0.jar \
     https://storage.googleapis.com/firebase-preview-drop/emulator/cloud-firestore-emulator-v1.22.0.jar
   echo "9b6498b7f62714d67f48f59b3818883cd682dbcd46b9f59511de81c97bb5166c  .cache/firebase/emulators/cloud-firestore-emulator-v1.22.0.jar" | sha256sum --check --strict
   ```
   Repeat with the new name and hash from `ci.yml` whenever the `firebase-tools` pin changes.
6. **Check once by hand:** `cd ~/ColPool/functions && npm run emulators:dev` prints the account table and "Emulators are ready and seeded". From Windows in that profile, `http://localhost:9099` answers. Ctrl+C to stop.
7. **Android Studio:** in that profile, clone the repo to a Windows folder if it has none, put `local.properties` (`PLACES_API_KEY=...`) at the repo root, File > Open the whole `ColPool` folder and Run **app** (step 1 above).

If it still fails: `No such file or directory` while the Ubuntu terminal works means the default distro is another one, usually `docker-desktop` (`wsl -l -v`, then `wsl --set-default Ubuntu-24.04`; owner-confirmed 2026-11-18); otherwise the clone isn't at `~/ColPool` (move it, or set `colpool.wslCheckout`); `npm: command not found` means nvm isn't at `~/.nvm` (redo step 3); a port in use means run `wsl pkill -f firebase` in a Windows terminal.

Alternative without a second WSL: set `colpool.autoStartEmulators=false` in that profile's `%USERPROFILE%\.gradle\gradle.properties` and start the emulators from the account that has the checkout before pressing Run; the build only checks that `localhost:9099` answers.

All accounts use the fake, demo-only password `demo-pass-123`. The table is printed in the run console after the emulators start.

| Email | Routing case |
|---|---|
| `dev.student@calbaptist.edu` | Student with a starting address: Search |
| `dev.newstudent@calbaptist.edu` | Student with no starting address: Onboarding |
| `dev.noconsent@calbaptist.edu` | Account with no consent record: blocked (RC-06) |
| `dev.staff@calbaptist.edu` | Institution staff (`institution: true`) |

The student accounts are created through the real `signUp` callable. Seeding is `functions/scripts/seed-dev-accounts.ts`; it refuses to run unless the Auth and Firestore emulator hosts are set, every emulator host (including the Functions host, if set) is loopback, and the project is a `demo-` one.

If the app shows the retry screen, the emulators aren't reachable or haven't finished starting. Debug builds also show the exception class and message under the generic error (`ui/ErrorDetail.kt`; emails, secrets and long tokens containing a digit are redacted); release builds never do. Debug emulator builds use a key-shaped fake API key (`"AIza"` plus 35 zeros, built at runtime in `ColPoolApp`) because the Firebase SDK rejects a non-key-shaped one on sign-up. Wait for "Emulators are ready and seeded" in the console, and check that WSL localhost forwarding works (from Windows, `http://localhost:9099` should answer).

No Android Studio run configuration is committed: Android Studio creates the **app** configuration itself, and the first committed one (#116) didn't work for the owner, so it was removed (#117). The one-command fallback is `cd ~/ColPool/functions && npm run emulators:dev` in WSL.

### Screens

Screen PRs carry images from **screenshot tests that render on the JVM**, with no device. They run in the sandbox and in CI. The owner does the real device check against the prototype screen during the staging review. The library is Roborazzi on Robolectric ([ADR 0001](docs/adr/0001-android-setup.md), owner decision 2026-11-11).

Sample data in screenshot tests matches what users will see. For profile option lists (gender, year, major, interests, commute style), use the full seeded lists from `functions/src/shared/schema/options.ts`, not a short subset, so the images show the real chip wrapping. Where the Android tests keep a Kotlin copy of those lists, a test checks it against that file so the two can't drift (owner decision 2026-11-19).

### Dependencies

Each setup Issue (#11, #12, #54) settles its whole library list as part of its setup decision: the architect proposes the list, the owner approves it once, and it's recorded in that Issue's ADR. Adding a library outside an approved list later is L2 (owner decision 2026-11-11).

### Setup preflight

For a setup Issue (#12, #54 and any later toolchain Issue), the lead does this before starting the implementer. Owner decision 2026-11-12; see `docs/agentic-retrospectives/issue-11.md`.

1. List what the Issue needs: tools and versions (CLIs, SDKs, JDK, Node), the folders they write (caches, config), the network hosts they download from, and any device or hardware (e.g. `/dev/kvm`).
2. Probe each one read-only from inside the sandbox: `which` / `--version`, `ls`, a write test in each cache folder, `curl -sI` per host. Compare with `.claude/settings.json` (`allowWrite`, `allowedDomains`). Don't install anything.
3. For every gap, write the exact fix: an install command checked against the real download URL or package name, a `settings.json` edit, or a restart.
4. Send the owner **one** HUMAN ACTION REQUIRED with all of them, in order. Re-run the probes after the owner is done, and start the implementer only when they pass.

### Institution accounts

Institution accounts are created only by `functions/scripts/create-institution-account.ts` (developer-run, never part of the deployed Functions code or the app; #15). It creates the Auth user and sets the custom claim `institution: true`. It creates no consent, user, profile or private-details docs. It refuses a non-`@calbaptist.edu` email, a password under 8 characters, and any email that already has an account (it never converts a student account). The password is read from the first line of stdin only, never from an argument, and is never printed.

- **Emulator:** with the Auth emulator running (`cd functions && npm run emulators`), in another shell with `FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099` set, in bash (Node 24+): `read -rs PW; printf '%s\n' "$PW" | npm run create-institution-account -- --email staff@calbaptist.edu --display-name "Test Staff"`. It targets project `demo-colpool`.
- **Production (documented only; run by the owner, never by an agent):** set `GOOGLE_APPLICATION_CREDENTIALS` to the path of a service-account key kept outside the repo, leave `FIREBASE_AUTH_EMULATOR_HOST` unset, and run the same command with `--project <real-project-id>`. A `demo-` project id is refused in this mode. Never commit or paste the key.

### Seeding reference data

`functions/scripts/seed-reference-data.ts` (developer-run, outside `src/` like the institution script; #17) writes the five `campusPoints/*` docs and `options/profile`. Values come from `functions/src/shared/schema/campusPoints.ts` and `options.ts`. It is idempotent (each doc is written whole with `set()`, so a second run changes nothing) and never deletes: a campus point removed from the seed list is left in place, so retire one by setting `active: false` in Firestore. Coordinate source: `docs/data-model.md` §2.7.

- **Emulator:** with the Firestore emulator running (`cd functions && npm run emulators`), in another shell with `FIRESTORE_EMULATOR_HOST=127.0.0.1:8080` set: `npm run seed-reference-data`. It targets project `demo-colpool`.
- **Production (documented only; run by the owner, never by an agent):** set `GOOGLE_APPLICATION_CREDENTIALS` to the path of a service-account key kept outside the repo, leave `FIRESTORE_EMULATOR_HOST` unset, and run `npm run seed-reference-data -- --project <real-project-id>`. A `demo-` project id is refused in this mode. Never commit or paste the key.

### Routes live check

`functions/scripts/routes-live-check.ts` (#28) calls the real Routes API a few times (3 calls) to confirm that a Riverside route via one stop to the Yeager Center gives plausible numbers (1 to 30 miles, 2 to 60 minutes) and that a Honolulu pickup gets "no route". It is never run in CI, and agents can't run it (no key, no network). **The owner runs it in their own terminal**, from a machine signed in to `gcloud` with access to Secret Manager:

```
cd functions && ROUTES_API_KEY="$(gcloud secrets versions access latest --secret=ROUTES_API_KEY --project colpool)" npm run routes:live-check -- --capture test/fixtures/routes/riverside-1stop.json
```

Without gcloud, type the key without echo: `read -s ROUTES_API_KEY && export ROUTES_API_KEY`, then `npm run routes:live-check -- --capture test/fixtures/routes/riverside-1stop.json`, then `unset ROUTES_API_KEY`.

It prints the UTC time, the call count, meters, seconds, miles, minutes and `PASS` or `FAIL` per check, never the key or headers. `--capture` writes only the first response body, replacing the provisional fixture; commit it after a look. Paste the output into the PR. See [ADR 0008](docs/adr/0008-route-preview.md) for why this runs after the merge.

### CI

`.github/workflows/ci.yml` (#54) runs on every pull request and on pushes to `dev`, `staging` and `main`, on GitHub-hosted runners (free, the repo is public). It uses no secrets, has `contents: read` only, never deploys, and every action is pinned to a commit SHA.

- **`android`:** JDK 21 (Temurin), `./gradlew assembleDebug testDebugUnitTest verifyRoborazziDebug` from the repo root. Reports are uploaded on failure.
- **`functions`:** Node 24, JDK 21, downloads the Firestore emulator jar and **fails if its SHA-256 differs** from the one in the workflow, then `npm ci`, `npm test` and `npm run test:e2e`. Logs are uploaded on failure. When the `firebase-tools` pin changes, update `FIRESTORE_JAR_NAME` and `FIRESTORE_JAR_SHA256` in the workflow with the same manual check as above.
- **`android-instrumented`:** runs `connectedDebugAndroidTest` on an API 36 x86_64 emulator, **only on PRs into `staging`**. It is informational: it is not in `ci-ok`, and the owner shouldn't make it a required check.
- **`ci-ok`:** needs both and fails if either failed or was cancelled. It is the single required check name.

"CI is green" means `ci-ok` passed on the PR. The verifier's own run still reports commands and results for the Issue's own tests.

**Owner steps (once, after #54 is merged and `ci-ok` has run on at least one PR, so GitHub offers the name):**
1. GitHub, repo **Settings > Rules > Rulesets > `dev-integration`**.
2. Turn on **Require status checks to pass**, click **Add checks**, pick **`ci-ok`** (the GitHub Actions one), and save.
3. Optional but sensible: do the same on **`promotion-gate`** so promotions to `staging` and `main` also need `ci-ok`. Because CI runs on `pull_request` for any base, the `dev` to `staging` PR gets it too.
4. Don't add `android` or `functions` as separate required checks: `ci-ok` covers them and keeps the name stable.

## 3. Sandbox

Configured in `.claude/settings.json`. It applies to the main session and every subagent.

**What's blocked:**
- Reads of the home folder, except the tool folders in `sandbox.filesystem.allowRead`; reads of `/mnt` and secret files (`.env`, `local.properties`, service accounts).
- Reads of the GitHub token (`~/.config/gh`). Ordinary commands can't see it.
- Writes outside the repo, the session temp folder and the Gradle and Android caches (`~/.gradle`, `~/.android`, via `sandbox.filesystem.allowWrite`). Inside `~/.gradle`, the files that configure or run code in every Gradle build stay blocked (`sandbox.filesystem.denyWrite`, §1).
- Network hosts not in `sandbox.network.allowedDomains`.

**What runs outside the sandbox** (`sandbox.excludedCommands`): only the GitHub script `node .claude/hooks/github.mjs` and the `next-issue` script. They are the only commands that can use the token. Both live in folders sandboxed commands can't write, and both call `/usr/bin/gh` and `/usr/bin/git` without a shell and with a minimal environment (`PATH`, `HOME`, `LANG`), so nothing from the Claude Code process, such as tokens, `TMPDIR` or `NODE_OPTIONS`, reaches them.

**Branching in the sandbox:** `.claude/` and `.git/config` are read-only, so a checkout that must rewrite `.claude/` files (for example switching to a stale local `dev`) stops halfway and leaves a mixed tree. Fetch, then branch straight from the remote: `git fetch origin` and `git checkout -b feature/<n>-<slug> origin/dev`. Never check out local `dev`. If a checkout does stop halfway, don't force it from the sandbox: ask the owner to run `git checkout -f -B <branch> origin/dev` (2026-11-25 lesson).

**The GitHub script** (`.claude/hooks/github.mjs`) is the only way agents reach GitHub. Run it with no operation for the full list.
- **Reads (any agent):** `auth-status`, `issue-view`, `issue-list`, `pr-view`, `pr-list`, `pr-checks`, `pr-diff`, `api-get` (paths under `repos/EthanScottWalden/ColPool/` or `users/EthanScottWalden/`).
- **Worktrees:** an agent working in a git worktree must call the script by its absolute path, `node /home/walde/ColPool/.claude/hooks/github.mjs <op>`. The owner's `settings.local.json` exempts that path from the sandbox. The relative path from a worktree points at the worktree's copy, and the guard rejects it.
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

`issue-view` and `pr-view` ask GitHub for a fixed list of fields and print them with a fixed template. Plain `gh issue view` asks for classic-Projects data, which GitHub now rejects.

**Owner review:** no PR into `dev` needs the owner's approval. The `dev-integration` ruleset's "Require review from Code Owners" is off and `.github/CODEOWNERS` was removed (owner decision 2026-11-16), so changes to `SPEC.md`, `INTENT.md`, `CLAUDE.md`, `docs/POLICY.md`, `.claude/` and `.github/` are reviewed in the `dev` → `staging` PR instead. Workflows under `.github/` run on `dev` before that review, so the lead flags any workflow change that reaches secrets in the PR brief.

Every other command (`curl`, `node`, `bash -c`, `gh` itself) runs inside the sandbox, where the token can't be read.

**Token scopes:** the helper account's token needs `project` for the next-issue script and for Project field updates (`gh auth refresh -s project`, run by the owner outside Claude Code).

`.claude/settings.local.json` (not committed) can set `sandbox.autoAllowBashIfSandboxed: true`, so sandboxed commands run without prompts. That's safe because sandboxed commands can't reach the token.

### Changing the allowlist

Never work around a sandbox block. When a command needs a host or folder the sandbox blocks:
1. The agent reports the exact host or path from the sandbox error to the lead.
2. The lead proposes the narrowest change (one host, not a wildcard; a tool cache, not the home folder) in the Issue's PR.
3. The owner approves the `.claude/settings.json` edit.

**What #12 did about them:**
- The Firebase CLI cache, config and emulator jars live in the project-local `.cache/` (git-ignored), set by `functions/scripts/emulator-env.mjs`, so no sandbox write entry was needed. npm uses `.cache/npm` through `functions/scripts/sandbox-npm.sh`.
- `storage.googleapis.com` stays off the allowlist. The owner downloads the Firestore emulator jar once (see §2).
- The Functions emulator listens on a Unix socket in the temp folder (`/tmp/claude-1000/fire_emu_*.sock`), which the sandbox blocks (`listen EPERM`). The sandbox is not changed: `npm test` uses only the Auth and Firestore emulators, and `npm run test:e2e` (Functions emulator) is for humans and CI.
