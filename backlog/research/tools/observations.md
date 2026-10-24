# Planning run observations

Purpose: one row per planning run, so the loop thresholds of `show-status.sh` (a link or a step text used 3 times, 5 steps without a new source) can be tuned from real runs. A row is added by the main thread when a run ends, whatever its outcome.

| date | topic | duration | sources read | signals that fired | verdict | note |
|---|---|---|---|---|---|---|
