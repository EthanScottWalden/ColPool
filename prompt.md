Do one last pass over SPEC, INTENT, ARCHITECTURE, POLICY, ROADMAP, and our Github Issues.

For ARCH:
- Verify the components/services, data/control flows, technology choices, external integrations, and deployment/runtime boundaries match the actual SDD and intended system.
- Confirm important boundaries, dependency directions, database/service access rules, security/trust boundaries, and architectural invariants are explicit enough that a future agent cannot easily misinterpret them.
- Final cleanup: remove duplicated SDD prose, task-specific Issue content, unsupported assumptions, and accidental policy/coding-style content. Preserve useful US/RC/DD or section references for traceability.

For POL:
- Verify roles/permissions, workflows, terminology, validation/data rules, privacy/security, and legal/ethical/organizational constraints accurately reflect the approved design.
- Separate policy from preference: remove coding style, tooling preferences, implementation sequence, and other choices that are not actually enduring product/domain rules.
- Final cleanup: remove duplicated SDD prose and unsupported assumptions; preserve useful US-## / RC-## / DD-## references where they improve traceability.

For ROADMAP:
- Verify the phase/module order, goals, and dependencies match the Implementation Plan and current approved scope.
- Check for duplication: remove Issue-by-Issue details, low-level acceptance criteria, status-field copies, and other content that would create a second task board.
- If you haven't already, include a clear statement and link establishing the GitHub Project and GitHub Issues as authoritative for detailed task definitions, sequencing, status, and work tracking.
- Cross-check GitHub: confirm the high-level roadmap direction is represented by the actual Project/Issues and flag/resolve meaningful inconsistencies.

For SPEC and INTENT, confirm both are up-to-date and update if needed.

For Github Issues, add/update issues as needed. Check #62 and #65 on the repository.