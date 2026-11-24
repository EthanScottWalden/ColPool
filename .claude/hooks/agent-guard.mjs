#!/usr/bin/env node
// Agent guard for ColPool (CLAUDE.md §9; owner decision 2026-11-24). PreToolUse hook on the Agent tool.
// Blocks starting an editing agent in a git worktree. Without worktrees, editing agents share the
// main checkout, so CLAUDE.md's "one implementer at a time, in the main checkout" rule follows.
// Stateless on purpose: no lock file that a killed agent could leave behind.
// Fails open only if the hook input itself can't be read.
import fs from "node:fs";

const EDITING_AGENTS = new Set(["implementer", "docs-keeper"]);

let input;
try {
  input = JSON.parse(fs.readFileSync(0, "utf8"));
} catch {
  process.exit(0);
}
const ti = input?.tool_input ?? {};
const type = String(ti.subagent_type ?? "");
if (EDITING_AGENTS.has(type) && ti.isolation === "worktree") {
  console.log(JSON.stringify({
    hookSpecificOutput: {
      hookEventName: "PreToolUse",
      permissionDecision: "deny",
      permissionDecisionReason:
        `'${type}' runs in the main checkout, one at a time, never in a worktree (CLAUDE.md §9). ` +
        "Start it without isolation once no other editing agent is running.",
    },
  }));
}
process.exit(0);