Remove the project's Docker containers, and only the volumes and caches the user names, without ever touching the working session's own container.

Usage: /docker-clean [--caches] [--reports] [--data] [--sonar] [--ci-history] [--orphans] [--builder-cache] [--all]

Run manually before a full run that must start on fresh containers. No flag removes containers only; every other scope is named by what it loses (the header of `scripts/docker-clean.sh` lists them). It is never run by another script or command.

Steps:
1. Run `bash scripts/docker-clean.sh <the flags the user named> --dry-run` and show its plan in full. Add only flags the user named; never add `--all`, `--data`, `--sonar`, `--ci-history`, `--orphans` or `--builder-cache` on your own.
2. Ask the user directly whether to remove what the plan lists, naming what is lost: data, run or analysis history, volumes that may belong to another project, the build cache shared by the whole host. Wait for a direct yes; a plan that lists only containers and caches still needs it.
3. After the yes, run the same command without `--dry-run` and with `--yes`; the session has no terminal for the typed confirmation, the user's yes is the confirmation.
4. Show the result: what was removed, what was kept and why, and the space freed. On a non-zero exit show the error lines.
