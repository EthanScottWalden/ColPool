#!/usr/bin/env node
// GitHub-write guard for ColPool (CLAUDE.md §2, §9).
//   pre  (PreToolUse):  deny GitHub writes from subagents other than `lead`; deny bursts of writes.
//   post (PostToolUse): after an Issue body/title edit, remind Claude to reset Human Review to Pending.
// Fails open: if the input can't be read, the hook allows the call rather than blocking all work.
import fs from "node:fs";
import path from "node:path";

const MODE = process.argv[2];
const MAX_PER_MINUTE = Number(process.env.GH_GUARD_MAX_WRITES_PER_MIN) || 6;
const WINDOW_MS = 60_000;

let input;
try {
  input = JSON.parse(fs.readFileSync(0, "utf8"));
} catch {
  process.exit(0);
}
const command = String(input?.tool_input?.command ?? "");
if (!/\bgh\s/.test(command)) process.exit(0);

// GraphQL queries often span lines, so judge them on the whole command: one write per mutation call.
const graphqlCalls = (command.match(/\bgh\s+api\s+graphql\b/g) || []).length;
const graphqlWrites = graphqlCalls && /\bmutation\b/.test(command) ? graphqlCalls : 0;
const graphqlIssueEdit = graphqlWrites > 0 && /\bupdateIssue\b/.test(command);

// Split the rest so each `gh` call is judged on its own.
const segments = command
  .split(/&&|\|\||;|\||\n/)
  .map((s) => s.trim())
  .filter((s) => s && !/\bgh\s+api\s+graphql\b/.test(s));

const WRITE_SUBCOMMANDS =
  /\bgh\s+(issue|pr|project|label|release|repo|gist|secret|variable|ruleset|workflow|run)\s+(create|edit|close|reopen|comment|merge|delete|item-add|item-edit|item-delete|item-archive|field-create|field-delete|transfer|lock|unlock|pin|unpin|develop|ready|review|rename|archive|set|enable|disable|cancel|rerun|copy|link|unlink|mark-template)\b/;

function isWrite(seg) {
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

const writeCount = segments.filter(isWrite).length + graphqlWrites;

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

if (writeCount === 0) process.exit(0);

function deny(reason) {
  console.log(JSON.stringify({
    hookSpecificOutput: { hookEventName: "PreToolUse", permissionDecision: "deny", permissionDecisionReason: reason },
  }));
  process.exit(0);
}

// Lead only. agent_type is set inside subagents, and also in a session started as `lead`.
const agentType = input.agent_type;
if (agentType && agentType !== "lead") {
  deny(`GitHub writes belong to the lead (CLAUDE.md §9). Agent '${agentType}' should report back to the lead instead.`);
}

// Pacing. State lives outside git (see .gitignore).
const projectDir = process.env.CLAUDE_PROJECT_DIR || input.cwd || process.cwd();
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
} catch {}
process.exit(0);
