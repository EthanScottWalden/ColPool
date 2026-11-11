#!/usr/bin/env node
// GitHub guard for ColPool (CLAUDE.md §2, §9; DEVELOPMENT.md §3).
//
// Why it is strict: `gh *`, `git push *` and the next-issue script run OUTSIDE the Bash sandbox
// (sandbox.excludedCommands), with the GitHub token readable. A command runs outside the sandbox
// only when it is one simple command, so that is the only shape this hook has to judge closely:
//   - `gh` and `git push` must be run on their own, with no shell expansion (no $, `, globs, !).
//     Compound commands that contain them are denied (they would run sandboxed, without the token).
//   - `gh`: an allowlist of subcommands. Subagents get read-only commands; the lead also gets the
//     writes it owns. Everything else (alias, extension, auth other than plain status, config,
//     ssh-key, codespace, secret, ...) is denied for everyone.
//   - `git push`: one fixed shape, `git push [-u] origin <branch>`; the lead may also
//     `git push origin --delete <branch>...`. Branch rules per agent; never dev/staging/main.
//   - Pacing: at most N GitHub writes (gh writes + pushes) a minute; if the counter can't be
//     saved, the write is denied.
// post (PostToolUse): after an Issue body/title edit, remind Claude to reset Human Review to Pending.
// Hooks run outside the sandbox. Fails open only if the hook input itself can't be read.
import fs from "node:fs";
import path from "node:path";

const MODE = process.argv[2];
const MAX_PER_MINUTE = Number(process.env.GH_GUARD_MAX_WRITES_PER_MIN) || 6;
const WINDOW_MS = 60_000;
const REPO = "EthanScottWalden/ColPool";
const PROTECTED = new Set(["dev", "staging", "main"]);
const PUSH_PREFIXES = { implementer: ["feature/", "fix/"], "docs-keeper": ["docs/"] };

let input;
try {
  input = JSON.parse(fs.readFileSync(0, "utf8"));
} catch {
  process.exit(0);
}
const command = String(input?.tool_input?.command ?? "");
// agent_id is set only inside a subagent; the main session (started as `lead`) has no agent_id.
const role = input.agent_id ? String(input.agent_type || "unknown-subagent") : "lead";
const cwd = input.cwd || process.env.CLAUDE_PROJECT_DIR || process.cwd();

function deny(reason) {
  console.log(JSON.stringify({
    hookSpecificOutput: { hookEventName: "PreToolUse", permissionDecision: "deny", permissionDecisionReason: reason },
  }));
  process.exit(0);
}

// ---------- Quote-aware shell tokenizer ----------
// Returns { segments: [[word, ...], ...], unsafe: bool }.
// Segments split on unquoted ; & | ( ) < > and newlines. `unsafe` flags anything the shell would
// expand or that this parser doesn't model: unquoted $ ` * ? [ ] { } ~ ! \ #, and $ or ` inside double quotes.
function tokenize(src) {
  const segments = [];
  let words = [];
  let word = null;
  let unsafe = false;
  const endWord = () => { if (word !== null) { words.push(word); word = null; } };
  const endSeg = () => { endWord(); if (words.length) segments.push(words); words = []; };
  for (let i = 0; i < src.length; i++) {
    const c = src[i];
    if (c === "'") {
      const j = src.indexOf("'", i + 1);
      if (j < 0) { unsafe = true; break; }
      word = (word ?? "") + src.slice(i + 1, j);
      i = j;
    } else if (c === '"') {
      let j = i + 1;
      let buf = "";
      while (j < src.length && src[j] !== '"') {
        if (src[j] === "$" || src[j] === "`") unsafe = true;
        if (src[j] === "\\" && j + 1 < src.length) { buf += src[j + 1]; j += 2; continue; }
        buf += src[j++];
      }
      if (j >= src.length) { unsafe = true; break; }
      word = (word ?? "") + buf;
      i = j;
    } else if (/\s/.test(c)) {
      if (c === "\n") endSeg(); else endWord();
    } else if (";&|()<>".includes(c)) {
      endSeg();
      segments.push(null); // marker: the command was compound
    } else {
      if ("$`*?[]{}~!\\#".includes(c)) unsafe = true;
      word = (word ?? "") + c;
    }
  }
  endSeg();
  const compound = segments.some((s) => s === null);
  return { segments: segments.filter(Boolean), compound, unsafe };
}

const { segments, compound, unsafe } = tokenize(command);
const isGh = (w) => w[0] === "gh";
// The git subcommand is the first word after `git` that isn't an option (or the value of -C / -c).
const isGitPush = (w) => {
  if (w[0] !== "git") return false;
  for (let i = 1; i < w.length; i++) {
    if (w[i] === "-C" || w[i] === "-c") { i++; continue; }
    if (!w[i].startsWith("-")) return w[i] === "push";
  }
  return false;
};
const sensitive = segments.filter((w) => isGh(w) || isGitPush(w));

// ---------- post: Human Review reminder ----------
function isIssueContentEdit(w) {
  if (!isGh(w)) return false;
  const a = stripGhGlobals(w.slice(1)).args;
  if (a[0] === "issue" && a[1] === "edit") return a.some((t) => /^(--body|--body-file|-b|-F|--title|-t)(=|$)/.test(t));
  if (a[0] === "api" && a.some((t) => /mutation/.test(t)) && a.some((t) => /updateIssue/.test(t))) return true;
  if (a[0] === "api" && a.some((t) => /repos\/[^\s]+\/issues\/\d+$/.test(t)))
    return a.some((t) => /^(body|title)=/.test(t));
  return false;
}

function stripGhGlobals(args) {
  const out = [];
  let repo = null;
  for (let i = 0; i < args.length; i++) {
    const t = args[i];
    if (t === "-R" || t === "--repo") { repo = args[++i] ?? ""; continue; }
    if (/^(-R|--repo=)/.test(t) && t !== "-R") { repo = t.replace(/^(-R|--repo=)/, ""); continue; }
    out.push(t);
  }
  return { args: out, repo };
}

if (MODE === "post") {
  if (segments.some(isIssueContentEdit)) {
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

if (sensitive.length === 0) process.exit(0);

// ---------- Shape rules ----------
if (compound || segments.length > 1) {
  deny("Run `gh` and `git push` on their own, not in a compound command (no &&, ;, |, $(...)). Use --jq for filtering and --body-file for long text (DEVELOPMENT.md §3).");
}
if (unsafe) {
  deny("`gh` and `git push` run outside the sandbox, so they can't use shell expansion ($, backticks, globs, !, \\). Put text in a file and use --body-file (DEVELOPMENT.md §3).");
}
const words = segments[0];
let writes = 0;

// ---------- git push ----------
if (words[0] === "git") {
  if (words[1] !== "push") deny("Use plain `git push [-u] origin <branch>` (no git options before `push`).");
  const rest = words.slice(2);
  const BRANCH = /^[A-Za-z0-9][A-Za-z0-9._\/-]*$/;
  const checkBranch = (b, deleting) => {
    if (!BRANCH.test(b) || b.includes("..") || b.endsWith("/") || b.endsWith(".lock") || /^(refs|heads|HEAD)(\/|$)/.test(b)) {
      deny(`'${b}' isn't a plain branch name. Push with \`git push -u origin <branch>\`.`);
    }
    if (PROTECTED.has(b)) deny(`'${b}' only changes through a PR (CLAUDE.md §5).`);
    if (deleting) return;
    if (role !== "lead") {
      const prefixes = PUSH_PREFIXES[role];
      if (!prefixes) deny(`Agent '${role}' doesn't push. Report back to the lead (CLAUDE.md §9).`);
      if (!prefixes.some((p) => b.startsWith(p))) deny(`Agent '${role}' may push only ${prefixes.join(" or ")} branches, not '${b}'.`);
    }
  };
  let ok = false;
  if ((rest.length === 2 && rest[0] === "origin") || (rest.length === 3 && ["-u", "--set-upstream"].includes(rest[0]) && rest[1] === "origin")) {
    checkBranch(rest[rest.length - 1], false);
    ok = true;
  } else if (rest.length >= 3 && ((rest[0] === "origin" && rest[1] === "--delete") || (rest[0] === "--delete" && rest[1] === "origin"))) {
    if (role !== "lead") deny("Only the lead deletes remote branches.");
    rest.slice(2).forEach((b) => checkBranch(b, true));
    ok = true;
  }
  if (!ok) deny("Allowed push shapes: `git push -u origin <branch>` or `git push origin <branch>`; the lead may also `git push origin --delete <branch>`. No force, globs, refspecs or other flags.");
  writes = 1;
}

// ---------- gh ----------
if (words[0] === "gh") {
  const { args, repo } = stripGhGlobals(words.slice(1));
  if (repo !== null && repo !== REPO) deny(`gh may only target ${REPO}.`);
  const [group, sub] = args;

  const READ = {
    issue: ["view", "list", "status"],
    pr: ["view", "list", "checks", "diff", "status"],
    project: ["view", "list", "item-list", "field-list"],
    repo: ["view"],
    run: ["list", "view"],
    workflow: ["list", "view"],
    label: ["list"],
    search: ["issues", "prs", "code", "commits", "repos"],
    release: ["list", "view"],
  };
  const LEAD_WRITE = {
    issue: ["create", "edit", "comment", "close", "reopen"],
    pr: ["create", "edit", "comment", "merge", "close", "reopen", "ready"],
    project: ["item-edit", "item-add"],
    label: ["create"],
  };

  if (group === "auth") {
    if (sub === "status" && args.length === 2) process.exit(0);
    deny("Only a plain `gh auth status` is allowed. Token and credential commands are blocked (DEVELOPMENT.md §3).");
  }

  if (group === "api") {
    const a = args.slice(1);
    const endpoint = a.find((t) => !t.startsWith("-"));
    let method = null;
    let hasFields = false;
    let hasInput = false;
    for (let i = 0; i < a.length; i++) {
      const t = a[i];
      let m;
      if (t === "-X" || t === "--method") method = (a[++i] || "").toUpperCase();
      else if ((m = t.match(/^(?:-X=?|--method=)(.+)$/))) method = m[1].toUpperCase();
      else if (/^(-f|-F|--field|--raw-field)$/.test(t)) { hasFields = true; i++; }
      else if (/^(-f|-F|--field=|--raw-field=)./.test(t)) hasFields = true;
      else if (t === "--input" || t.startsWith("--input=")) hasInput = true;
    }
    const isGraphql = endpoint === "graphql";
    const isRead = !isGraphql && !hasFields && !hasInput && (method === null || method === "GET");
    if (isRead) process.exit(0);
    if (role !== "lead") deny(`Agent '${role}' may use only read-only \`gh api\` (GET, no fields, no graphql). Report back to the lead (CLAUDE.md §9).`);
    if (isGraphql) {
      const body = a.join(" ");
      if (hasInput || /query=@/.test(body)) deny("Pass GraphQL queries inline with -f query='...', not from a file, so the guard can see them.");
      writes = /\bmutation\b/.test(body) ? 1 : 0;
    } else {
      writes = 1;
    }
  } else if (READ[group]?.includes(sub)) {
    process.exit(0);
  } else if (LEAD_WRITE[group]?.includes(sub)) {
    if (role !== "lead") deny(`GitHub writes belong to the lead (CLAUDE.md §9). Agent '${role}' should report back instead.`);
    writes = 1;
  } else {
    deny(`\`gh ${[group, sub].filter(Boolean).join(" ")}\` isn't on the allowed list. Allowed: read commands for issues, PRs, projects, runs and the API; lead writes for issues, PRs and project items (DEVELOPMENT.md §3).`);
  }
}

if (writes === 0) process.exit(0);

// ---------- Pacing ----------
const projectDir = process.env.CLAUDE_PROJECT_DIR || cwd;
const stateDir = path.join(projectDir, ".claude", "hooks", ".state");
const stateFile = path.join(stateDir, "gh-writes.json");
const now = Date.now();
let recent = [];
try {
  recent = JSON.parse(fs.readFileSync(stateFile, "utf8")).filter((t) => now - t < WINDOW_MS);
} catch {}

if (recent.length + writes > MAX_PER_MINUTE) {
  const waitS = Math.ceil((WINDOW_MS - (now - Math.min(...recent))) / 1000);
  deny(
    `GitHub write pacing: ${recent.length} writes in the last minute (limit ${MAX_PER_MINUTE}). ` +
      `Wait about ${waitS}s and retry one write at a time (CLAUDE.md §2).`
  );
}

try {
  fs.mkdirSync(stateDir, { recursive: true });
  fs.writeFileSync(stateFile, JSON.stringify([...recent, ...Array(writes).fill(now)]));
} catch (e) {
  deny(`GitHub write pacing state couldn't be saved (${e.code || e.message}), so the write is blocked. Check ${stateFile}.`);
}
process.exit(0);
