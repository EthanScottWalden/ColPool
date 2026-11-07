You are helping me create docs/ARCHITECTURE.md for this software project.

Use the current SDD architecture/platform sections, architectural requirements/constraints and design decisions, the architecture diagram, and the relevant Implementation Plan sections, and treat them as authoritative unless specified otherwise. Do not redesign the product or silently replace approved technology choices.

Pay particularly close attention to implementation plan and SDD portions that materially affect architecture. 
For SDD (SPEC.md) these may include:
- Section 5, 
- architectural RC-## items from Section 4, 
- and relevant DD/OQ/RISK items from Section 7.

And for the implementation plan (INTENT.md):
- Section 1 scope/guardrails, 
- Section 2 phase summary, 
- important Section 3 dependencies/references, 
- Section 4/4.1 sequencing, 
- and Section 6 agent guardrails.

Reference other sections as needed if they also affect architecture.

Create a concise, durable architecture document intended for future human developers and AI coding agents. Organize it so an implementation agent can quickly understand:
- System purpose and architecture at a glance
- Major components/services and their responsibilities
- Important data/control flows and external integrations
- Component/module boundaries and allowed dependency directions
- Approved technology/platform choices and decisions that should not be casually replaced
- Architectural security/privacy/trust boundaries
- Architectural invariants, guardrails, and "must/never" rules
- Important deployment/runtime boundaries when relevant
- Unresolved architectural questions or risks that must NOT be guessed
- Pointers back to authoritative SDD/Implementation Plan sections where useful instead of duplicating long prose

Keep task-specific feature instructions and backlog details OUT of this file; those belong in GitHub Issues. Keep domain/business policies OUT unless they directly constrain architecture; those belong in docs/POLICY.md later down the road.

After producing the initial draft, identify missing or ambiguous architectural decisions and INTERVIEW ME with focused questions that would materially improve this document. Do not invent answers. Incorporate my answers only after I respond.

Aim for a document that is concise enough for an AI agent to use repeatedly but complete enough to prevent important architectural drift.