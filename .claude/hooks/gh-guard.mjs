#!/usr/bin/env node
// GitHub guard for ColPool (CLAUDE.md §2, §9; DEVELOPMENT.md "Sandbox").
//   pre  (PreToolUse):
//     - never print the GitHub token (`gh auth token`, `--show-token`);
//     - no curl/wget/httpie to GitHub hosts (use `gh`, which the guard can see);
//     - `gh` writes: lead only;
//     - `git push`: lead any non-protected branch, implementer feature/* and fix/*,
//       docs-keeper docs/*; nobody pushes dev/staging/main, force-pushes or pushes --all/--mirror;
//       only the lead deletes remote branches;
//     - pacing: at most N GitHub writes (gh writes + pushes) a minute.
//   post (PostToolUse): after an Issue body/title edit, remind Claude to reset Human Review to Pending.
// Hooks run outside the Bash sandbox, so the state file below is writable.
// Fails open only if the hook input itself can't be read.
import fs from "node:fs";
import path from "node:path";
import { execFileSync } from "node:child_process";

const MODE = process.argv[2];
const MAX_PER_MINUTE = Number(process.env.GH_GUARD_MAX_WRITES_PER_MIN) || 6;
const WINDOW_MS = 60_000;
const PROTECTED = new Set(["dev", "staging", "main"]);
const PUSH_PREFIXES = {
  implementer: ["feature/", "fix/"],
  "docs-keeper": ["docs/"],
};

let input;
try {
  input = JSON.parse(fs.readFileSync(0, "utf8"));
} catch {
  process.exit(0);
}
const command = String(input?.tool_input?.command ?? "");
if (!/\b(gh|git|curl|wget|https?)\b/.test(command)) process.exit(0);

// agent_id is set only inside a subagent; the main session (started as `lead`) has agent_type but no agent_id.
const role = input.agent_id ? String(input.agent_type || "unknown-subagent") : "lead";
const cwd = input.cwd || process.env.CLAUDE_PROJECT_DIR || process.cwd();

function deny(reason) {
  console.log(JSON.stringify({
    hookSpecificOutput: { hookEventName: "PreToolUse", permissionDecision: "deny", permissionDecisionReason: reason },
  }));
  process.exit(0);
}

// GraphQL queries often span lines, so judge them on the whole command: one write per mutation call.
const graphqlCalls = (command.match(/\bgh\s+api\s+graphql\b/g) || []).length;
const graphqlWrites = graphqlCalls && /\bmutation\b/.test(command) ? graphqlCalls : 0;
const graphqlIssueEdit = graphqlWrites > 0 && /\bupdateIssue\b/.test(command);

// Split the rest so each call is judged on its own.
const segments = command
  .split(/&&|\|\||;|\||\n/)
  .map((s) => s.trim())
  .filter((s) => s && !/\bgh\s+api\s+graphql\b/.test(s));

const WRITE_SUBCOMMANDS =
  /\bgh\s+(issue|pr|project|label|release|repo|gist|secret|variable|ruleset|workflow|run)\s+(create|edit|close|reopen|comment|merge|delete|item-add|item-edit|item-delete|item-archive|field-create|field-delete|transfer|lock|unlock|pin|unpin|develop|ready|review|rename|archive|set|enable|disable|cancel|rerun|copy|link|unlink|mark-template)\b/;

function isGhWrite(seg) {
  if (WRITE_SUBCOMMANDS.test(seg)) return true;
  if (!/\bgh\s+api\b/.test(seg)) return false;
  const method = seg.match(/(?:-X|--method)[\s=]+([A-Za-z]+)/);
  if (method) return method[1].toUpperCase() !== "GET";
  // gh api defaults to POST when fields or input are given.
  return /\s(-f|-F|--field|--raw-field|--input)\b/.test(seg);
}

function isIssueContentEdit(seg) {
  if (/\bgh\s+issue\s+edit\b/.test(seg)) return /\s(--body|--body-file|-b|-F|--title|-t)\b/.test(seg);
  if (/\bgh\s+api\b/.test(seg) && /repos\/[^\s]+\/issues\/\d+(\s|$)/.test(seg))
    return /(-X|--method)[\s=]+PATCH/i.test(seg) && /\b(body|title)=/.test(seg);
  return false;
}

if (MODE === "post") {
  if (graphqlIssueEdit || segments.some(isIssueContentEdit)) {
    console.log(JSON.stringify({
      hookSpecificOutput: {
        hookEventName: "PostToolUse",
        additionalContext:
          "An Issue's body or title was edited. Per CLAUDE.md §2, set that Issue's Project 'Human Review' field back to Pending (one paced write).",
      },
    }));
  }
  process.exit(0);
}

// 1. Never expose the token.
if (/\bgh\s+auth\s+token\b/.test(command) || /\bgh\s+auth\s+status\b[^\n]*(\s-t\b|--show-token)/.test(command)) {
  deny("Printing the GitHub token is not allowed (DEVELOPMENT.md, Sandbox).");
}

// 2. GitHub only through gh / git push, where this guard can see the call.
const GITHUB_HOST = /\b(api\.)?github\.com\b|githubusercontent\.com|\bgithub\.io\b/;
for (const seg of segments) {
  if (/(^|\s|\/)(curl|wget|http|https|xh)\s/.test(seg + " ") && GITHUB_HOST.test(seg)) {
    deny("Reach GitHub with `gh` or `git`, not curl/wget/httpie, so the write guard applies (CLAUDE.md §9).");
  }
}

// 3. git push rules.
function currentBranch() {
  try {
    return execFileSync("git", ["rev-parse", "--abbrev-ref", "HEAD"], { cwd, encoding: "utf8" }).trim();
  } catch {
    return "";
  }
}

function parsePush(seg) {
  const tokens = seg.split(/\s+/).filter(Boolean);
  const gitIdx = tokens.findIndex((t) => t === "git" || t.endsWith("/git"));
  if (gitIdx < 0) return null;
  const pushIdx = tokens.indexOf("push", gitIdx + 1);
  if (pushIdx < 0) return null;
  // Allow only global options between `git` and `push` (e.g. -C dir); otherwise "push" is an argument.
  const between = tokens.slice(gitIdx + 1, pushIdx);
  if (between.some((t, i) => !t.startsWith("-") && !["-C", "-c"].includes(between[i - 1]))) return null;
  const flags = [];
  const positional = [];
  const takesValue = new Set(["-o", "--push-option", "--repo", "--receive-pack", "--exec"]);
  const rest = tokens.slice(pushIdx + 1);
  for (let i = 0; i < rest.length; i++) {
    const t = rest[i];
    if (t.startsWith("-")) {
      flags.push(t);
      if (takesValue.has(t)) i++;
    } else positional.push(t);
  }
  const refspecs = positional.slice(1);
  return { flags, refspecs };
}

let pushWrites = 0;
for (const seg of segments) {
  const push = parsePush(seg);
  if (!push) continue;
  pushWrites++;
  const { flags, refspecs } = push;
  if (flags.some((f) => /^(--force|-f|--force-with-lease|--force-if-includes)/.test(f) || /^-[a-zA-Z]*f/.test(f) && !f.startsWith("--")) ||
      refspecs.some((r) => r.startsWith("+"))) {
    deny("Force-pushes are not allowed (CLAUDE.md §5).");
  }
  if (flags.some((f) => ["--all", "--mirror", "--prune"].includes(f))) {
    deny("Push one named branch at a time; --all, --mirror and --prune are not allowed.");
  }
  if (flags.includes("--tags") && role !== "lead") deny("Only the lead pushes tags.");
  const deleting = flags.includes("--delete") || flags.includes("-d") || refspecs.some((r) => r.startsWith(":"));
  const targets = refspecs.length
    ? refspecs.map((r) => {
        const dst = r.includes(":") ? r.split(":").pop() : r;
        const name = dst === "HEAD" || dst === "" ? currentBranch() : dst;
        return name.replace(/^refs\/heads\//, "");
      })
    : [currentBranch()];
  for (const branch of targets) {
    if (!branch) deny("Couldn't tell which branch this push targets. Name it explicitly: `git push -u origin <branch>`.");
    if (PROTECTED.has(branch)) {
      deny(`'${branch}' only changes through a PR (CLAUDE.md §5). Push your own branch and ask the lead to open the PR.`);
    }
    if (deleting && role !== "lead") deny("Only the lead deletes remote branches.");
    if (role !== "lead") {
      const prefixes = PUSH_PREFIXES[role];
      if (!prefixes) deny(`Agent '${role}' doesn't push. Report back to the lead (CLAUDE.md §9).`);
      if (!prefixes.some((p) => branch.startsWith(p))) {
        deny(`Agent '${role}' may push only ${prefixes.join(" or ")} branches, not '${branch}'.`);
      }
    }
  }
}

// 4. gh writes: lead only.
const ghWrites = segments.filter(isGhWrite).length + graphqlWrites;
if (ghWrites > 0 && role !== "lead") {
  deny(`GitHub writes belong to the lead (CLAUDE.md §9). Agent '${role}' should report back to the lead instead.`);
}

const writeCount = ghWrites + pushWrites;
if (writeCount === 0) process.exit(0);

// 5. Pacing. State lives outside git (see .gitignore).
const projectDir = process.env.CLAUDE_PROJECT_DIR || cwd;
const stateDir = path.join(projectDir, ".claude", "hooks", ".state");
const stateFile = path.join(stateDir, "gh-writes.json");
const now = Date.now();
let recent = [];
try {
  recent = JSON.parse(fs.readFileSync(stateFile, "utf8")).filter((t) => now - t < WINDOW_MS);
} catch {}

if (recent.length + writeCount > MAX_PER_MINUTE) {
  const waitS = Math.ceil((WINDOW_MS - (now - Math.min(...recent))) / 1000);
  deny(
    `GitHub write pacing: ${recent.length} writes in the last minute (limit ${MAX_PER_MINUTE}). ` +
      `Wait about ${waitS}s and retry one write at a time, or batch them into a single GraphQL mutation (CLAUDE.md §2).`
  );
}

try {
  fs.mkdirSync(stateDir, { recursive: true });
  fs.writeFileSync(stateFile, JSON.stringify([...recent, ...Array(writeCount).fill(now)]));
} catch (e) {
  deny(`GitHub write pacing state couldn't be saved (${e.code || e.message}), so the write is blocked. Check ${stateFile}.`);
}
process.exit(0);
