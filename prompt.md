You are helping me create or refine the project-level CLAUDE.md for this repository.

Treat these as authoritative inputs:
- docs/ARCHITECTURE.md
- docs/POLICY.md
- docs/ROADMAP.md
- the GitHub Project and reviewed GitHub Issues
- any current DEVELOPMENT.md, test/run instructions, or repository conventions I provide

Goal: create a concise project operating guide that Claude should know in every session. It should NOT duplicate the detailed architecture, policy, roadmap, or Issue content.

First, DO NOT edit files. Propose a CLAUDE.md structure and identify any missing decisions/questions I should answer. Include only information that should apply across many tasks, such as:
- authoritative sources and when to consult them
- reviewed GitHub Issues as the source of task-specific intent
- project build/run/test commands and Definition-of-Done expectations
- development branch / PR / promotion rules
- documentation expectations
- when Claude may proceed independently
- when Claude must stop and ask me because a decision affects architecture, security, policy, scope, UX, destructive operations, production data, or another high-consequence boundary
- pointers to reusable agents/skills/rules where appropriate

Keep the proposed CLAUDE.md compact and easy to scan. Prefer referencing project files by path over copying their contents. Do not @import large documents unless we intentionally want their full contents loaded every session.

Flag contradictions instead of guessing. Stop after the proposal and questions.

After I approve or revise the proposal, create/update the repository-root CLAUDE.md and then give me a short checklist of what I should manually verify.

Also, read the Claude Agentic Operating Model pdf, ignore most of it for now, but we will incorporate sections 6 and 7 into the CLAUDE.md.