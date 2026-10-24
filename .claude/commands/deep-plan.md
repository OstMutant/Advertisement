Plan a task in depth with the `deep-planner` agent (Opus) -- best practices from the web, the project's own practice, a compared choice, and two plans: one for people and one for AI executors. The planner works in a topic folder under `backlog/research/` and never edits project files.

Usage: /deep-plan <path of a backlog task file, or a short description of what to plan>
Examples:
  /deep-plan backlog/tasks/<task-file>.md
  /deep-plan "move the audit timeline to server-side paging"

Steps:
1. Resolve the input. A path of an existing file under `backlog/tasks/` is the task file; any other
   text is the description of a task that has no file yet.
2. Choose the topic name: lower-case letters, digits and hyphens, at most 40 characters, taken from
   the task file name or the description. Run `bash backlog/research/tools/scripts/list-topics.sh`.
   If that topic already exists it is a research to resume: read its `brief.md`, run
   `show-status.sh <topic>` and tell the user what is there instead of starting over.
3. Run `bash backlog/research/tools/scripts/start-topic.sh <topic>`.
4. Write `backlog/research/<topic>/brief.md` from the template: the goal; the task (the link of the
   task file or "none yet"); the context (the decisions, constraints and rejected options from this
   conversation, written out in full -- the planner sees none of the conversation); where to look in
   the project; the completion criterion; the decisions already taken; what the planner may decide
   (by default nothing beyond the goal: the planner highlights a conflict with best practice or with the
   project's practice and leaves the decision to the user); the available executor models (haiku, sonnet,
   opus).
5. Show the user what will run: the topic, the brief in a few lines and that the planner is Opus with
   no token or time limit, only its own stop rule. Ask whether to start it. Do not start without a
   direct yes.
6. Start the planner in the background: `Agent({description: "Deep plan <topic>", subagent_type:
   "deep-planner", prompt: "Topic: <topic>", run_in_background: true})`. Give it nothing but the
   topic name; everything else is in `brief.md`.
7. While it runs, watch it with `bash backlog/research/tools/scripts/show-status.sh <topic> 3`
   (through `Monitor` with an until-loop that prints a line only when the status or the step count
   changes; never read the agent's transcript). Tell the user in one short line each time the
   picture changes: `progressing` with the step count means it is on track; a long run is said
   to need more time and why; `warning` is named with its signal; `looping` is reported at once as
   trouble. Stay silent on routine steps.
8. When the planner returns, read its reply (`PLAN READY` or `PLAN BLOCKED`). For `PLAN BLOCKED` show
   the reason and leave the folder in place. For `PLAN READY` read `plan-human.md` and present it to
   the user in the Approval Rule format (the plain-language layer from the human plan with the cost,
   the risks and the differences from the project up front, then the machine plan's summary:
   number of steps, executor models, estimated cost), then ask for approval.
9. After approval: file the task or update the task file so that it holds the human plan as its
   description and the machine plan as a section "Execution plan" (use `/new-task` for a task
   without a file), add the run to `backlog/research/tools/observations.md` (date, topic, duration,
   sources read, signals that fired from the last `show-status.sh`, verdict, one-line note), and only
   then run `bash backlog/research/tools/scripts/remove-topic.sh <topic> --yes`.
10. Never execute the plan from this command, and never commit: execution is a separate approval.
