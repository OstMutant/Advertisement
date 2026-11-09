---
description: Rewrite AI instructions (commands, rules, skills, agents, CLAUDE.md, or any file or folder of them) to be more concise, precise and strict without losing a requirement -- the goal is written first, the best practices for that kind of structure are researched for that goal, every rewrite comes with a coverage table and a summary of what changed and what benefit it brings against the goal, and is applied file by file only after approval. Never commits.
argument-hint: "<path of a file or folder> [task <id>]"
disable-model-invocation: false
---

# /tighten-ai

Make the instructions the AI follows shorter, more exact and stricter, so that they still make the AI
work as well as before or better. The text gets smaller; what it requires stays whole.

Hard limits: never change what an instruction requires; never delete a rule that is not stated
elsewhere; never touch a file outside the given path; never write a file without approval; never commit.

## Steps

1. **Scope.** `$ARGUMENTS` is the path of a file or folder of AI instructions, optionally followed by
   `task <id>`: the task in whose scope the run happens. List the files with their lines and characters
   and the kind of each by its place (command, rules, skill, agent, project instructions). Any other file
   is refused.
2. **Goal.** Before anything else the goal must be written exactly: what the instructions must achieve
   and what the shortening is for. The user states it or confirms the wording you propose; with a task id
   the goal goes into that task under `## Tighten goal`. Without a confirmed goal the run does not go on.
3. **Research.** For each kind of structure in the scope, for the stated goal, follow `/deep-research`
   (topic `tighten-ai-<kind>`; when the topic exists, show its status instead of starting over). The research
   concentrates on Claude's own practices for that kind of structure, found on the web in Anthropic's
   documentation and other primary sources, with links. The best practices from its plan, with the checklist
   below, are the standard the rewrite is judged against. Asking before removing the topic
   folder is `/deep-research`'s own rule.
4. **Proposal, per file.** Read the whole file, then write: the new text; lines and characters before and
   after; a coverage table with every requirement of the old text and the line of the new text that keeps it;
   what changed into what; the benefit against the goal. Show it on screen and, with a task id, write it
   into the task.
5. **Check.** Dispatch a read-only agent (`model: "sonnet"`) with the old text, the new text and the
   coverage table; it reports every requirement that is lost, weakened or changed. A finding is fixed in the
   proposal before it is shown for approval.
6. **Apply.** Per `.claude/rules.md` Approval Rule, ask whether to write this file, and only after a direct
   yes write it and `git add` it; stop after each file.
7. **Summary.** At the end show on screen, and with a task id write into the task: the files, the totals
   before and after, what changed into what, the benefit against the goal, and what was left unchanged.

## Checklist

Items 1-14 hold for any instruction file (command, rules file, skill, agent definition, `CLAUDE.md`);
items 15-20 only for command files.

1. The file opens with its goal in one or two sentences.
2. Every instruction is concrete and checkable (a command, a path, a condition, a count), never "properly", "as needed", "where useful".
3. Every behaviour is stated once; no two instructions conflict.
4. Wherever a rule covers more than one item, its scope is explicit ("every", "each", "only", "not ..."); nothing relies on Claude generalising.
5. A fact owned by another file (a skill, `.claude/rules.md`, a script header, a generator) is referenced by name, never restated.
6. No story: no history, dates, incidents or before/after; a reason stays only as one clause where the rule is not obvious.
7. Nothing Claude already knows is explained; the file stays below 500 lines.
8. One term per concept, used everywhere in the file.
9. The content that must survive compaction comes first (Claude Code keeps the first 5,000 tokens of an invoked command and does not re-read it).
10. Markdown headers, numbered steps where order or completeness matters, short bullets; no dense paragraphs.
11. Exact commands for fragile operations; judgement wording for judgement work.
12. Hard limits are in one place, as one short list.
13. No count or "today" fact that changes with the code; derive it at run time or reference its home.
14. A rule that must hold every time and could be checked by a script or hook is flagged to the user, never moved silently.
15. Frontmatter, when the project chose to have it: `description` says what and when, key use first; `argument-hint` lists the argument forms; model invocation stays enabled when another command calls it.
16. Every argument form and its exact effect is in one table or list.
17. Steps are numbered; each says what it prints or writes and when the run stops.
18. The final report is a fixed template.
19. A verification step precedes "done" (re-read, check, script).
20. Every dispatched agent's prompt carries what it needs (scope, rules, model, read-only), since a subagent sees no conversation.
