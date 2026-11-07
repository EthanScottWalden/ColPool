You are helping me create docs/POLICY.md for this software project.

Use the SDD user roles/stories, acceptance criteria, requirements/constraints, scope/non-goals, relevant design decisions/open questions, and the applicable Implementation Plan guardrails. Do not invent new product policy. Treat SDD and implementation plan as authoritative.

Create a concise, durable policy document containing rules that should remain true across many implementation tasks. Organize it around the categories that actually apply to this project, such as:
- User roles, permissions, and authorization expectations
- Domain/business rules and required workflows
- User-facing terminology and meaning of important domain concepts
- Validation/data-handling rules
- Privacy/security rules that are policy rather than architecture
- Legal, ethical, organizational, or compliance constraints
- Important non-goals or prohibited behavior that implementation must preserve
- Unresolved policy questions that must NOT be guessed
- Traceability to relevant US-## / RC-## / DD-## items where useful

Keep architecture structure and technology boundaries OUT unless they are necessary to state the policy; those belong in docs/ARCHITECTURE.md. Keep coding style, test commands, and personal developer preferences OUT; those belong in CLAUDE.md, skills/rules, or development documentation later.

After producing the initial draft, identify missing or ambiguous product/domain policies and INTERVIEW ME with focused questions. Do not invent answers. Incorporate my answers only after I respond.

Aim for a document that a future implementation agent can consult to answer, "What enduring rules must my implementation obey?"