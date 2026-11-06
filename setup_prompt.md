You are helping me convert my completed software design into an agent-ready implementation backlog.

First, carefully review the authoritative project artifacts available in this repository/workspace:
- Final Software Design Document (SDD)
- Completed Implementation Plan
- UI/UX flow diagrams and Figma screenshots/mockups
- Existing GitHub Project configuration and fields
- Existing repository structure, if applicable

Your goal is to create a complete set of GitHub Issues that a future AI software-development agent could implement with minimal ambiguity.

DO NOT create any GitHub Issues yet. First produce a proposed Issue breakdown for my review.

For each proposed Issue, include:
- Clear, concise, outcome-oriented title
- Context / intended outcome
- Relevant SDD user-story IDs and requirements/constraints (US-## / RC-## / DD-## where useful)
- Implementation Plan phase
- Detailed implementation context
- Objective acceptance criteria / definition of done
- Relevant UI/UX screenshots, Figma frames, architecture references, or links
- Dependencies / prerequisite Issues
- Testing or validation expectations
- Important architectural or policy constraints
- Proposed Phase, Sequence, Priority, Area, Status, Human Review, and other applicable GitHub Project fields

Decompose the work into units large enough to produce meaningful progress but small enough that an autonomous coding agent can implement and review them independently. Avoid both giant multi-feature Issues and trivial micro-Issues.

Identify any ambiguity, missing requirement, contradiction, or design decision that should be resolved by me rather than guessed by an implementation agent.

After presenting the proposed backlog, STOP and wait for my review.

Once I approve or revise the proposed backlog, use the authenticated GitHub tooling available in this environment to:
1. Create the approved GitHub Issues.
2. Add them to the existing GitHub Project.
3. Populate the appropriate Project fields.
4. Set Human Review = Pending for every newly created Issue.
5. Establish useful sequencing/dependencies where supported.
6. Include the relevant UI/UX images or links in the Issue bodies. If direct image upload is unavailable, place appropriate images in a project documentation/issue-assets location and reference them clearly from the Issue.

Do not invent missing requirements. Ask me whenever a decision materially affects functionality, architecture, policy, security, scope, or user experience.