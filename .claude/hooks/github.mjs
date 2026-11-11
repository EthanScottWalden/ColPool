#!/usr/bin/env node
// ColPool's only path to GitHub with the helper account's token (DEVELOPMENT.md §3).
//
// This script runs outside the Bash sandbox (sandbox.excludedCommands), so it can use the token.
// It never runs a shell: it calls `gh` and `git` with fixed argument lists, and it allows only the
// operations below. Raw `gh` and `git push` run inside the sandbox, where the token can't be read.
// It lives in .claude/hooks/, which sandboxed commands can't write.
//
// Who may run which operation is checked by gh-guard.mjs (the PreToolUse hook), using the
// ROLE_OPS table exported here. This script enforces everything else, however it is invoked.
//
// Usage: node .claude/hooks/github.mjs <operation> [args]   (run with no operation for help)
import { execFileSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

export const REPO = "EthanScottWalden/ColPool";
export const PROJECT_OWNER = "EthanScottWalden";
export const PROTECTED = new Set(["dev", "staging", "main"]);
export const PUSH_PREFIXES = { lead: null, implementer: ["feature/", "fix/"], "docs-keeper": ["docs/"] };

// Read operations: any agent. Write operations: the lead, except `push` (see PUSH_PREFIXES).
export const READ_OPS = new Set([
  "help", "auth-status", "issue-view", "issue-list", "pr-view", "pr-list", "pr-checks", "pr-diff", "api-get", "graphql-query",
]);
export const WRITE_OPS = new Set([
  "issue-create", "issue-edit", "issue-comment", "issue-close", "issue-reopen",
  "pr-create", "pr-edit", "pr-comment", "pr-merge", "pr-close",
  "graphql-mutation", "branch-delete", "push",
]);
// Mutations the lead may send with graphql-mutation (Project fields, Issue links).
const ALLOWED_MUTATIONS = new Set([
  "updateProjectV2ItemFieldValue", "addProjectV2ItemById", "clearProjectV2ItemFieldValue",
  "addBlockedBy", "removeBlockedBy", "addSubIssue", "removeSubIssue",
]);

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..", "..");
const BRANCH = /^[A-Za-z0-9][A-Za-z0-9._-]*(\/[A-Za-z0-9][A-Za-z0-9._-]*)*$/;
const MAX_FILE = 256 * 1024;

function fail(msg) {
  process.stderr.write(`github.mjs: ${msg}\n`);
  process.exit(2);
}

// Absolute paths, so a planted `gh` or `git` earlier on PATH is never run with the token.
const GH = "/usr/bin/gh";
const GIT = "/usr/bin/git";
const gh = (args, opts = {}) =>
  execFileSync(GH, args, { cwd: ROOT, encoding: "utf8", maxBuffer: 64 * 1024 * 1024, stdio: ["pipe", "inherit", "inherit"], ...opts });
const ghOut = (args) => execFileSync(GH, args, { cwd: ROOT, encoding: "utf8", maxBuffer: 64 * 1024 * 1024 });

// --- argument helpers ---
function parse(argv, spec) {
  // spec: { flags: {name: "value"|"bool"}, positional: n }
  const flags = {};
  const pos = [];
  for (let i = 0; i < argv.length; i++) {
    const t = argv[i];
    if (t.startsWith("--")) {
      const name = t.slice(2);
      const kind = spec.flags?.[name];
      if (!kind) fail(`unknown option ${t}`);
      if (kind === "bool") flags[name] = true;
      else {
        if (i + 1 >= argv.length) fail(`${t} needs a value`);
        (flags[name] ??= []).push(argv[++i]);
      }
    } else pos.push(t);
  }
  if (pos.length !== (spec.positional ?? 0)) fail(`expected ${spec.positional ?? 0} argument(s), got ${pos.length}`);
  return { flags, pos };
}
const one = (flags, name, required = true) => {
  const v = flags[name];
  if (!v) { if (required) fail(`--${name} is required`); return undefined; }
  if (v.length > 1) fail(`--${name} given twice`);
  return v[0];
};
const num = (s) => { if (!/^[1-9][0-9]{0,6}$/.test(s)) fail(`'${s}' isn't an Issue or PR number`); return s; };
const text = (s, max = 256) => { if (s.length > max || /[\x00-\x08\x0b-\x1f]/.test(s)) fail("text is too long or has control characters"); return s; };

// Body files: only from the repo (outside .git) or the session temp folders, never secrets.
function readBodyFile(p) {
  let real;
  try { real = fs.realpathSync(p); } catch { fail(`can't read ${p}`); }
  const tmpRoots = [process.env.TMPDIR, `/tmp/claude-${os.userInfo().uid}`].filter(Boolean).map((d) => { try { return fs.realpathSync(d); } catch { return null; } }).filter(Boolean);
  const under = (dir) => real === dir || real.startsWith(dir + path.sep);
  const inRepo = under(ROOT) && !under(path.join(ROOT, ".git")) && !under(path.join(ROOT, ".claude", "hooks", ".state"));
  if (!inRepo && !tmpRoots.some(under)) fail(`body files must be in the repo or the session temp folder, not ${real}`);
  const base = path.basename(real);
  if (/^\.env|local\.properties$|secrets\.properties$|google-services\.json$|service-account|firebase-adminsdk|\.runtimeconfig\.json$|hosts\.yml$|\.git-credentials$/.test(base)) {
    fail(`refusing to send ${base}`);
  }
  const st = fs.statSync(real);
  if (!st.isFile() || st.size > MAX_FILE) fail(`${base} isn't a regular file under ${MAX_FILE / 1024} KB`);
  const body = fs.readFileSync(real, "utf8");
  if (/\b(gh[opsu]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|AIza[0-9A-Za-z_-]{30,})/.test(body)) fail(`${base} looks like it contains a token or API key`);
  return body;
}

function checkBranch(b) {
  if (!BRANCH.test(b) || b.includes("..") || b.endsWith(".lock") || /^(refs|heads|HEAD)(\/|$)/i.test(b)) fail(`'${b}' isn't a plain branch name`);
  if (PROTECTED.has(b.toLowerCase())) fail(`'${b}' only changes through a PR (CLAUDE.md §5)`);
  return b;
}

function graphqlText(s) {
  if (s.length > 20000) fail("query too long");
  return s;
}
const stripStrings = (q) => q.replace(/"""[\s\S]*?"""|"(?:[^"\\]|\\.)*"/g, '""').replace(/#[^\n]*/g, "");

// --- operations ---
const HELP = `Operations (node .claude/hooks/github.mjs <op> ...):
 Read (any agent):
  auth-status
  issue-view <n> [--comments]            issue-list [--state open|closed|all] [--label L] [--limit N]
  pr-view <n> [--comments]               pr-list [--state open|closed|merged|all] [--limit N]
  pr-checks <n>                          pr-diff <n>
  api-get <repos/${REPO}/...|users/${PROJECT_OWNER}/...> [--jq EXPR] [--paginate]
  graphql-query --query-file F [--var name=value]... [--jq EXPR]      (no mutations)
 Write (lead; push also implementer feature/* fix/*, docs-keeper docs/*):
  issue-create --title T --body-file F [--label L]...
  issue-edit <n> [--title T] [--body-file F] [--add-label L]... [--remove-label L]...
  issue-comment <n> --body-file F        issue-close <n>       issue-reopen <n>
  pr-create --base dev|staging --head BRANCH --title T --body-file F
  pr-edit <n> [--title T] [--body-file F]   pr-comment <n> --body-file F   pr-close <n>
  pr-merge <n>                           (PRs into dev only; merge commit; never --admin)
  graphql-mutation --query-file F [--var name=value]...   (Project field and Issue-link mutations only)
  branch-delete <branch>                 (not dev/staging/main)
  push <branch>                          (git push -u origin <branch> for the current branch's commits)
 Body and query files must be in the repo or the session temp folder.`;

const OPS = {
  help: () => console.log(HELP),
  "auth-status": () => gh(["auth", "status"]),

  "issue-view": (a) => { const { flags, pos } = parse(a, { flags: { comments: "bool" }, positional: 1 }); gh(["issue", "view", num(pos[0]), "-R", REPO, ...(flags.comments ? ["--comments"] : [])]); },
  "issue-list": (a) => {
    const { flags } = parse(a, { flags: { state: "value", label: "value", limit: "value" } });
    const args = ["issue", "list", "-R", REPO];
    const st = one(flags, "state", false); if (st) { if (!["open", "closed", "all"].includes(st)) fail("bad --state"); args.push("--state", st); }
    for (const l of flags.label ?? []) args.push("--label", text(l, 50));
    const lim = one(flags, "limit", false); if (lim) args.push("--limit", num(lim));
    gh(args);
  },
  "pr-view": (a) => { const { flags, pos } = parse(a, { flags: { comments: "bool" }, positional: 1 }); gh(["pr", "view", num(pos[0]), "-R", REPO, ...(flags.comments ? ["--comments"] : [])]); },
  "pr-list": (a) => {
    const { flags } = parse(a, { flags: { state: "value", limit: "value" } });
    const args = ["pr", "list", "-R", REPO];
    const st = one(flags, "state", false); if (st) { if (!["open", "closed", "merged", "all"].includes(st)) fail("bad --state"); args.push("--state", st); }
    const lim = one(flags, "limit", false); if (lim) args.push("--limit", num(lim));
    gh(args);
  },
  "pr-checks": (a) => { const { pos } = parse(a, { positional: 1 }); gh(["pr", "checks", num(pos[0]), "-R", REPO]); },
  "pr-diff": (a) => { const { pos } = parse(a, { positional: 1 }); gh(["pr", "diff", num(pos[0]), "-R", REPO]); },
  "api-get": (a) => {
    const { flags, pos } = parse(a, { flags: { jq: "value", paginate: "bool" }, positional: 1 });
    const p = pos[0];
    if (!/^[A-Za-z0-9_.\/?=&,-]+$/.test(p) || p.includes("..") || !(p.startsWith(`repos/${REPO}/`) || p.startsWith(`users/${PROJECT_OWNER}/`))) {
      fail(`api-get paths must start with repos/${REPO}/ or users/${PROJECT_OWNER}/`);
    }
    const args = ["api", "--method", "GET", p];
    if (flags.paginate) args.push("--paginate");
    const jq = one(flags, "jq", false); if (jq) args.push("--jq", text(jq, 2000));
    gh(args);
  },
  "graphql-query": (a) => graphql(a, false),
  "graphql-mutation": (a) => graphql(a, true),

  "issue-create": (a) => {
    const { flags } = parse(a, { flags: { title: "value", "body-file": "value", label: "value" } });
    const args = ["issue", "create", "-R", REPO, "--title", text(one(flags, "title")), "--body-file", "-"];
    for (const l of flags.label ?? []) args.push("--label", text(l, 50));
    gh(args, { input: readBodyFile(one(flags, "body-file")) });
  },
  "issue-edit": (a) => {
    const { flags, pos } = parse(a, { flags: { title: "value", "body-file": "value", "add-label": "value", "remove-label": "value" }, positional: 1 });
    const args = ["issue", "edit", num(pos[0]), "-R", REPO];
    const t = one(flags, "title", false); if (t) args.push("--title", text(t));
    for (const l of flags["add-label"] ?? []) args.push("--add-label", text(l, 50));
    for (const l of flags["remove-label"] ?? []) args.push("--remove-label", text(l, 50));
    const bf = one(flags, "body-file", false);
    if (bf) args.push("--body-file", "-");
    if (args.length === 5) fail("nothing to edit");
    gh(args, bf ? { input: readBodyFile(bf) } : {});
  },
  "issue-comment": (a) => { const { flags, pos } = parse(a, { flags: { "body-file": "value" }, positional: 1 }); gh(["issue", "comment", num(pos[0]), "-R", REPO, "--body-file", "-"], { input: readBodyFile(one(flags, "body-file")) }); },
  "issue-close": (a) => { const { pos } = parse(a, { positional: 1 }); gh(["issue", "close", num(pos[0]), "-R", REPO]); },
  "issue-reopen": (a) => { const { pos } = parse(a, { positional: 1 }); gh(["issue", "reopen", num(pos[0]), "-R", REPO]); },

  "pr-create": (a) => {
    const { flags } = parse(a, { flags: { base: "value", head: "value", title: "value", "body-file": "value" } });
    const base = one(flags, "base");
    const head = one(flags, "head");
    if (base === "dev") { checkBranch(head); }
    else if (base === "staging") { if (head !== "dev") fail("PRs into staging come from dev only (CLAUDE.md §5)"); }
    else fail("PRs go into dev, or from dev into staging (CLAUDE.md §5)");
    gh(["pr", "create", "-R", REPO, "--base", base, "--head", head, "--title", text(one(flags, "title")), "--body-file", "-"], { input: readBodyFile(one(flags, "body-file")) });
  },
  "pr-edit": (a) => {
    const { flags, pos } = parse(a, { flags: { title: "value", "body-file": "value" }, positional: 1 });
    const args = ["pr", "edit", num(pos[0]), "-R", REPO];
    const t = one(flags, "title", false); if (t) args.push("--title", text(t));
    const bf = one(flags, "body-file", false); if (bf) args.push("--body-file", "-");
    if (args.length === 5) fail("nothing to edit");
    gh(args, bf ? { input: readBodyFile(bf) } : {});
  },
  "pr-comment": (a) => { const { flags, pos } = parse(a, { flags: { "body-file": "value" }, positional: 1 }); gh(["pr", "comment", num(pos[0]), "-R", REPO, "--body-file", "-"], { input: readBodyFile(one(flags, "body-file")) }); },
  "pr-close": (a) => { const { pos } = parse(a, { positional: 1 }); gh(["pr", "close", num(pos[0]), "-R", REPO]); },
  "pr-merge": (a) => {
    const { pos } = parse(a, { positional: 1 });
    const n = num(pos[0]);
    const info = JSON.parse(ghOut(["pr", "view", n, "-R", REPO, "--json", "baseRefName,headRefName,state"]));
    if (info.baseRefName !== "dev") fail(`PR #${n} targets ${info.baseRefName}; agents merge only into dev (CLAUDE.md §5)`);
    if (info.state !== "OPEN") fail(`PR #${n} is ${info.state}`);
    gh(["pr", "merge", n, "-R", REPO, "--merge"]);
  },

  "branch-delete": (a) => {
    const { pos } = parse(a, { positional: 1 });
    const b = checkBranch(pos[0]);
    execFileSync(GIT, ["push", "origin", "--delete", b], { cwd: ROOT, stdio: "inherit" });
  },
  push: (a) => {
    const { pos } = parse(a, { positional: 1 });
    const b = checkBranch(pos[0]);
    execFileSync(GIT, ["push", "-u", "origin", `refs/heads/${b}:refs/heads/${b}`], { cwd: ROOT, stdio: "inherit" });
  },
};

function graphql(a, mutation) {
  const { flags } = parse(a, { flags: { "query-file": "value", var: "value", jq: "value" } });
  const q = graphqlText(readBodyFile(one(flags, "query-file")));
  const bare = stripStrings(q);
  const isMutation = /^\s*mutation\b/.test(bare);
  if (/\bsubscription\b/.test(bare)) fail("subscriptions aren't allowed");
  if (!mutation && /\bmutation\b/.test(bare)) fail("graphql-query is for reads; mutations go through graphql-mutation (lead only)");
  if (mutation) {
    if (!isMutation) fail("graphql-mutation needs a mutation");
    // Every field called with arguments must be an allowed mutation or a read inside its payload selection.
    const top = bare.slice(bare.indexOf("{") + 1);
    const depth0 = [];
    let d = 0;
    for (const m of top.matchAll(/[{}]|(?:\w+\s*:\s*)?(\w+)\s*\(/g)) {
      if (m[0] === "{") d++;
      else if (m[0] === "}") d--;
      else if (d === 0) depth0.push(m[1]);
    }
    if (!depth0.length) fail("no mutation field found");
    const bad = depth0.filter((f) => !ALLOWED_MUTATIONS.has(f));
    if (bad.length) fail(`mutation(s) not allowed: ${bad.join(", ")}. Allowed: ${[...ALLOWED_MUTATIONS].join(", ")}`);
  }
  const args = ["api", "graphql", "-f", `query=${q}`];
  for (const v of flags.var ?? []) {
    const m = v.match(/^([A-Za-z_][A-Za-z0-9_]*)=([\s\S]*)$/);
    if (!m) fail(`bad --var ${v}`);
    if (m[2].startsWith("@")) fail("--var values can't read files");
    args.push("-f", `${m[1]}=${text(m[2], 1000)}`);
  }
  const jq = one(flags, "jq", false); if (jq) args.push("--jq", text(jq, 2000));
  gh(args);
}

if (process.argv[1] && fileURLToPath(import.meta.url) === fs.realpathSync(process.argv[1])) {
  const [op, ...rest] = process.argv.slice(2);
  if (!op || op === "--help") { console.log(HELP); process.exit(0); }
  const fn = OPS[op];
  if (!fn) fail(`unknown operation '${op}'. Run with no arguments for help.`);
  try { fn(rest); } catch (e) { if (e.status !== undefined) process.exit(e.status || 1); throw e; }
}
