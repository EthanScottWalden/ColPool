#!/usr/bin/env node
// GitHub guard for ColPool (CLAUDE.md §2, §9; DEVELOPMENT.md §3). PreToolUse/PostToolUse hook on Bash.
//
// The only commands that run outside the sandbox with the GitHub token are
// `.claude/hooks/github.mjs` (the trusted GitHub script) and the next-issue script. github.mjs
// enforces what each operation may do. This hook decides WHO may run which operation:
//   - any command that mentions github.mjs must be exactly `node .claude/hooks/github.mjs <op> ...`,
//     on its own, with no shell expansion. Wrappers (timeout, env, nohup, ...) are denied.
//   - read operations: any agent. Write operations: the lead. `push`: the lead, implementer
//     (feature/*, fix/*) and docs-keeper (docs/*).
//   - pacing: at most N writes a minute; if the counter can't be saved, the write is denied.
//   - raw `gh` / `git push` would run sandboxed without the token, so they are denied with a hint.
// post: after an Issue body or title edit, remind the lead to reset Human Review to Pending.
// Hooks run outside the sandbox. Fails open only if the hook input itself can't be read.
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { READ_OPS, WRITE_OPS, PUSH_PREFIXES } from "./github.mjs";

const MODE = process.argv[2];
const MAX_PER_MINUTE = Number(process.env.GH_GUARD_MAX_WRITES_PER_MIN) || 6;
const WINDOW_MS = 60_000;
const HOOK_DIR = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HOOK_DIR, "..", "..");
const TOOL = path.join(HOOK_DIR, "github.mjs");

let input;
try {
  input = JSON.parse(fs.readFileSync(0, "utf8"));
} catch {
  process.exit(0);
}
const command = String(input?.tool_input?.command ?? "");
// agent_id is set only inside a subagent; the main session (started as `lead`) has no agent_id.
const role = input.agent_id ? String(input.agent_type || "unknown-subagent") : "lead";
const cwd = input.cwd || process.env.CLAUDE_PROJECT_DIR || ROOT;

function deny(reason) {
  console.log(JSON.stringify({
    hookSpecificOutput: { hookEventName: "PreToolUse", permissionDecision: "deny", permissionDecisionReason: reason },
  }));
  process.exit(0);
}

// Quote-aware tokenizer. Splits on unquoted ; & | ( ) < > and newlines. `unsafe` flags shell
// expansion this parser doesn't model: unquoted $ ` * ? [ ] { } ~ ! \ #, and $ or ` in double quotes.
function tokenize(src) {
  const segments = [];
  let words = [];
  let word = null;
  let unsafe = false;
  let compound = false;
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
    } else if (c === "\n") {
      endSeg(); compound = true;
    } else if (/\s/.test(c)) {
      endWord();
    } else if (";&|()<>".includes(c)) {
      endSeg(); compound = true;
    } else {
      if ("$`*?[]{}~!\\#".includes(c)) unsafe = true;
      word = (word ?? "") + c;
    }
  }
  endSeg();
  return { segments, compound: compound || segments.length > 1, unsafe };
}

const { segments, compound, unsafe } = tokenize(command);

// Commands that only read or stage files; mentioning github.mjs in them is fine (e.g. `git add`).
const SAFE_READERS = new Set(["git", "grep", "rg", "cat", "head", "tail", "sed", "ls", "diff", "wc", "file", "stat", "less"]);
const WRAPPERS = new Set(["env", "timeout", "nohup", "nice", "time", "stdbuf", "command", "exec", "xargs", "sudo", "setsid", "ionice", "builtin"]);
function commandWord(w) {
  let i = 0;
  while (i < w.length && (/^[A-Za-z_][A-Za-z0-9_]*=/.test(w[i]) || WRAPPERS.has(path.basename(w[i])) || /^-/.test(w[i]) || /^[0-9.]+[smhd]?$/.test(w[i]))) i++;
  return [path.basename(w[i] || ""), w.slice(i + 1)];
}
// Any segment that mentions an unsandboxed script and doesn't START with a plain reader counts as
// an invocation, so a wrapper (`env -u cat node ...`) can't run it without being judged below.
// Nothing is skipped when deciding a segment is a reader.
const mentions = (w, name) => w.some((t) => t.includes(name)) && !(SAFE_READERS.has(w[0]) && !(w[0] === "git" && w.includes("push")));
const mentionsTool = segments.some((w) => mentions(w, "github.mjs"));
const NEXT_ISSUE = path.join(ROOT, ".claude", "skills", "next-issue", "scripts", "next-issue.mjs");
if (MODE !== "post" && segments.some((w) => mentions(w, "next-issue.mjs"))) {
  const w = segments[0];
  const ok = !compound && !unsafe && w[0] === "node" && w[1] && path.resolve(cwd, w[1]) === NEXT_ISSUE &&
    w.slice(2).every((t, k, rest) => t === "--json" || t === "--limit" || (rest[k - 1] === "--limit" && /^[0-9]{1,3}$/.test(t)));
  if (!ok) deny("Run the next-issue script exactly as `node .claude/skills/next-issue/scripts/next-issue.mjs [--json] [--limit N]`, on its own.");
}

// ---------- post ----------
if (MODE === "post") {
  const w = segments[0] || [];
  if (mentionsTool && !compound && w[2] === "issue-edit" && w.some((t) => t === "--title" || t === "--body-file")) {
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

// ---------- raw gh / git push: hint ----------
for (const w of segments) {
  const [cmd, rest] = commandWord(w);
  const sub = rest.find((t, i) => !t.startsWith("-") && !["-C", "-c"].includes(rest[i - 1]));
  const wrapped = WRAPPERS.has(path.basename(w[0])) && w.some((t) => path.basename(t) === "gh");
  if (cmd === "gh" || wrapped || (cmd === "git" && sub === "push")) {
    deny("Raw `gh` and `git push` run inside the sandbox without the GitHub token. Use `node .claude/hooks/github.mjs <op>` (run it with no op for the list; DEVELOPMENT.md §3).");
  }
}

if (!mentionsTool) process.exit(0);

// ---------- github.mjs ----------
if (compound || unsafe) {
  deny("Run `node .claude/hooks/github.mjs <op> ...` on its own: no &&, ;, |, $(...), globs or expansion. Put long text in a file and pass --body-file (DEVELOPMENT.md §3).");
}
const w = segments[0];
const scriptOk = w[0] === "node" && w[1] && path.resolve(cwd, w[1]) === TOOL;
if (!scriptOk) deny("Call the GitHub script exactly as `node .claude/hooks/github.mjs <op> ...` (no wrappers such as timeout or env).");

const op = w[2];
let writes = 0;
if (op === undefined || op === "--help" || READ_OPS.has(op)) {
  process.exit(0);
} else if (op === "push") {
  if (!(role in PUSH_PREFIXES)) deny(`Agent '${role}' doesn't push. Report back to the lead (CLAUDE.md §9).`);
  const prefixes = PUSH_PREFIXES[role];
  const branch = w[3] || "";
  if (prefixes && !prefixes.some((p) => branch.startsWith(p))) deny(`Agent '${role}' may push only ${prefixes.join(" or ")} branches, not '${branch}'.`);
  writes = 1;
} else if (WRITE_OPS.has(op)) {
  if (role !== "lead") deny(`GitHub writes belong to the lead (CLAUDE.md §9). Agent '${role}' should report back instead.`);
  writes = 1;
} else {
  deny(`Unknown github.mjs operation '${op}'. Run it with no operation for the list.`);
}

// ---------- pacing ----------
const projectDir = process.env.CLAUDE_PROJECT_DIR || ROOT;
const stateDir = path.join(projectDir, ".claude", "hooks", ".state");
const stateFile = path.join(stateDir, "gh-writes.json");
const now = Date.now();
let recent = [];
try {
  recent = JSON.parse(fs.readFileSync(stateFile, "utf8")).filter((t) => now - t < WINDOW_MS);
} catch {}
if (recent.length + writes > MAX_PER_MINUTE) {
  const waitS = Math.ceil((WINDOW_MS - (now - Math.min(...recent))) / 1000);
  deny(`GitHub write pacing: ${recent.length} writes in the last minute (limit ${MAX_PER_MINUTE}). Wait about ${waitS}s and retry one write at a time (CLAUDE.md §2).`);
}
try {
  fs.mkdirSync(stateDir, { recursive: true });
  fs.writeFileSync(stateFile, JSON.stringify([...recent, ...Array(writes).fill(now)]));
} catch (e) {
  deny(`GitHub write pacing state couldn't be saved (${e.code || e.message}), so the write is blocked. Check ${stateFile}.`);
}
process.exit(0);
