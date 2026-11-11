Prompt 1

Audit this project's agentic development configuration and recommend the smallest set of reusable rules/capabilities that would materially improve reliability.

Review:
- CLAUDE.md
- .claude/agents/
- docs/ARCHITECTURE.md and docs/POLICY.md
- current build/test/review commands
- Git/PR/promotion rules
- representative reviewed GitHub Issues

DO NOT create or modify extensions yet.

For each recurring behavior or rule you think should be captured, classify it as ONE of the following (or "no additional configuration needed"):
1. CLAUDE.md - always-on project guidance
2. .claude/rules/ - topic/path-specific persistent guidance
3. .claude/skills//SKILL.md - reusable knowledge or reasoning-heavy workflow
4. Hook/shared setting in .claude/settings.json (and .claude/hooks/ script if needed) - deterministic event-driven behavior or hard guardrail
5. Existing GitHub Ruleset / repository protection - trust/promotion enforcement that should remain outside Claude instructions

For each recommendation explain:
- the trigger/problem it solves
- why this mechanism is better than the alternatives
- expected benefit
- possible failure/risk
- whether it is truly needed for this project now

Favor targeted permissions and deterministic enforcement over broad bypasses. Never recommend storing secrets in committed configuration. Preserve the human-controlled staging/main promotion gate.

Stop after the recommendation table and wait for my review. After I approve specific items, implement ONLY those approved items and summarize the resulting files/configuration.

After this is done, I want to consider setting stuff up for sandboxing. When we're done with the above we can talk about that too.

------------------------------

Prompt 2

Do NOT implement or modify anything yet.

Read the project-level CLAUDE.md, project agent definitions, applicable .claude/rules/, relevant Skills, shared Claude settings/Hooks, docs/ARCHITECTURE.md, docs/POLICY.md, docs/ROADMAP.md, and any other project instructions that CLAUDE.md identifies as authoritative.

Then summarize, in concise bullets:
1. The authoritative sources of project requirements and persistent context.
2. Where task-specific intent comes from and how you know an Issue is approved for implementation.
3. The Git/branching rules you must follow, including what AiDev may do in dev and what remains human-controlled for staging/main/production.
4. The project agent roles available to you, when each should be used, and the major handoffs/escalations.
5. The testing/review/documentation evidence required before work is considered complete.
6. Which reusable Skills/rules/Hooks or deterministic guardrails apply and when.
7. Decisions you may make autonomously versus decisions that require human input.
8. Any contradiction, missing information, unsafe permission, or ambiguous instruction you found.

Do not guess how to resolve contradictions. Do not change files. Stop after the summary and wait for my review.