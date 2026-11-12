Help me perform a first-run retrospective for the agentic workflow used on GitHub Issue #.

Review the Issue, PR/commits, available Claude session evidence, tests/validation, Project status history, current CLAUDE.md, agent definitions, Skills/rules/Hooks/settings, and the persistent docs that governed the task.

DO NOT change the configuration yet.

Draft docs/AGENTIC_RETROSPECTIVE.md (about one page or less) with:
- Issue / outcome summary
- What the workflow did well without human correction
- One or more meaningful problems, surprises, unnecessary behaviors, missed rules, or human interventions
- Evidence for each observation
- Likely root cause (for example: unclear CLAUDE.md, missing context, weak Issue, overlapping agent roles, poor delegation, missing/incorrect Skill or Hook, weak test/review rule, permission problem, or another design issue)
- The single highest-leverage configuration/workflow improvement you recommend
- Why that change should improve the next development run

Then list any additional candidate improvements separately, but do not add complexity merely because a feature exists.

Stop and wait for my review BEFORE modifying configuration.

After I approve the improvement, apply only the approved change, update the retrospective with the final change made, and tell me what focused verification I should run to confirm the new configuration is understood.