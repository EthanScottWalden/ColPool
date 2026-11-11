#!/usr/bin/env node
// Lists ColPool Issues eligible for work (CLAUDE.md §2), ranked, using one paged GraphQL read.
// Eligible: Status = Ready, Human Review = Approved, Issue open, every "blocked by" Issue closed.
// Rank: Phase, then Sequence, then Priority (P0 first), then Issue number.
// Usage: node next-issue.mjs [--json] [--limit N]
import { execFileSync } from "node:child_process";
import { userInfo } from "node:os";

const OWNER = "EthanScottWalden";
const PROJECT = 1;
const args = process.argv.slice(2);
const asJson = args.includes("--json");
const limitArg = args.indexOf("--limit");
const limit = limitArg >= 0 ? Number(args[limitArg + 1]) || 5 : 5;

const QUERY = `query($after: String) {
  user(login: "${OWNER}") { projectV2(number: ${PROJECT}) {
    items(first: 100, after: $after) {
      pageInfo { hasNextPage endCursor }
      nodes {
        fieldValues(first: 30) { nodes {
          ... on ProjectV2ItemFieldSingleSelectValue { name field { ... on ProjectV2FieldCommon { name } } }
          ... on ProjectV2ItemFieldNumberValue { number field { ... on ProjectV2FieldCommon { name } } }
          ... on ProjectV2ItemFieldTextValue { text field { ... on ProjectV2FieldCommon { name } } }
        } }
        content { ... on Issue {
          number title state url
          labels(first: 20) { nodes { name } }
          blockedBy(first: 30) { nodes { number state } }
        } }
      }
    }
  } }
}`;

function fetchItems() {
  const items = [];
  let after = null;
  do {
    const ghArgs = ["api", "graphql", "-f", `query=${QUERY}`];
    if (after) ghArgs.push("-f", `after=${after}`);
    // Absolute gh and a minimal environment: this script runs outside the sandbox (DEVELOPMENT.md §3).
    const env = { PATH: "/usr/bin:/bin", HOME: userInfo().homedir, LANG: "C.UTF-8", GH_PROMPT_DISABLED: "1" };
    const out = JSON.parse(execFileSync("/usr/bin/gh", ghArgs, { encoding: "utf8", env, maxBuffer: 32 * 1024 * 1024 }));
    const page = out.data.user.projectV2.items;
    items.push(...page.nodes);
    after = page.pageInfo.hasNextPage ? page.pageInfo.endCursor : null;
  } while (after);
  return items;
}

function toIssue(item) {
  const c = item.content;
  if (!c || c.number === undefined) return null; // draft items or PRs
  const f = {};
  for (const v of item.fieldValues.nodes) {
    if (!v.field) continue;
    f[v.field.name] = v.name ?? v.number ?? v.text;
  }
  const num = (x) => (x === undefined || x === null || x === "" ? Infinity : Number(x));
  return {
    number: c.number,
    title: c.title,
    url: c.url,
    open: c.state === "OPEN",
    labels: c.labels.nodes.map((l) => l.name),
    status: f["Status"] ?? "(none)",
    review: f["Human Review"] ?? "(none)",
    phase: num(f["Phase"]),
    sequence: num(f["Sequence"]),
    priority: f["Priority"] ?? "P9",
    openBlockers: c.blockedBy.nodes.filter((b) => b.state === "OPEN").map((b) => b.number),
  };
}

function reasons(i) {
  const r = [];
  if (!i.open) r.push("closed");
  if (i.status !== "Ready") r.push(`status ${i.status}`);
  if (i.review !== "Approved") r.push(`human review ${i.review}`);
  if (i.openBlockers.length) r.push(`blocked by ${i.openBlockers.map((n) => "#" + n).join(", ")}`);
  return r;
}

const rank = (a, b) =>
  a.phase - b.phase || a.sequence - b.sequence || a.priority.localeCompare(b.priority) || a.number - b.number;

const issues = fetchItems().map(toIssue).filter(Boolean);
const eligible = issues.filter((i) => reasons(i).length === 0).sort(rank);
const upcoming = issues
  .filter((i) => i.open && reasons(i).length > 0 && i.status !== "Done")
  .sort(rank)
  .slice(0, limit);

if (asJson) {
  console.log(JSON.stringify({ eligible, upcoming: upcoming.map((i) => ({ ...i, notEligibleBecause: reasons(i) })) }, null, 2));
  process.exit(0);
}

const line = (i) =>
  `#${i.number} [P${i.phase === Infinity ? "?" : i.phase}·S${i.sequence === Infinity ? "?" : i.sequence}·${i.priority}]` +
  `${i.labels.length ? " {" + i.labels.join(", ") + "}" : ""} ${i.title}`;

console.log(`Eligible (${eligible.length}):`);
if (eligible.length === 0) console.log("  none");
for (const i of eligible) console.log("  " + line(i));
console.log(`\nNext in line but not eligible (top ${upcoming.length}):`);
for (const i of upcoming) console.log(`  ${line(i)}\n      not eligible: ${reasons(i).join("; ")}`);
