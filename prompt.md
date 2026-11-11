Firebase: https://console.firebase.google.com/project/colpool/

Help me design the smallest useful project-specific agent model for this repository.

Review these authoritative inputs:
- project CLAUDE.md
- docs/ARCHITECTURE.md
- docs/POLICY.md
- docs/ROADMAP.md
- representative reviewed GitHub Issues

Also review the Agentic Engineering Operating Model pdf (not necessarily) authoritative, just use as a reference.

DO NOT create agent files yet. First propose the agent model for my review.

For each proposed agent, provide:
- name
- short description that clearly states WHEN Claude should delegate to it
- purpose / responsibilities
- when it should NOT be used
- authoritative inputs it should read
- expected output / handoff
- tool or permission boundaries that would add value
- skills to preload, if any
- whether isolated worktree execution would genuinely help
- human escalation conditions
- handoffs/dependencies with other agents

Optimize for clarity and minimal overlap. Do not create agents merely to mirror an organizational chart. Keep work in the lead/current agent when a separate context or specialization does not add real value.

Identify duplicated ownership, ambiguous decision authority, and places where the human must remain the final decision maker. Stop after the proposal and wait for my review.

After I approve the model, create the project-specific definitions under .claude/agents/ and summarize what you created.

From the PDF, I would at least like the following agents:
- Lead
- Combined agent that is both product owner and domain expert
- Architect
- Implementation agent
- Combined agent that is UX advocate, QA, and security

I would also be interested in an agent that keeps our documentation consistent.