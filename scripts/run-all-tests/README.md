# scripts/run-all-tests

The daily-iteration test loop — one command that runs the whole reactor build, unit tests,
integration tests, and Playwright end-to-end tests together, instead of invoking each suite by
hand. Exists purely as a convenience grouping on top of `scripts/build-and-test.sh` and
`scripts/playwright.sh` — it never reimplements either suite's own logic.

## Flow

Entry point: `run.sh`.

```mermaid
flowchart LR
    R[run.sh] --> C{"--archunit-metrics?"}
    C -->|yes| B["build-and-test.sh<br/>--unit --integration<br/>--archunit-metrics"]
    C -->|no| B2["build-and-test.sh<br/>--unit --integration"]
    R --> D{"--reset?"}
    D -->|yes| D1["deploy-and-run.sh<br/>--reset"] --> P[playwright.sh]
    D -->|no| D2["deploy-and-run.sh<br/>--reset-only-db"] --> P
    B --> S[summary]
    B2 --> S
    P --> S
```

See `run.sh`'s own header for the full parallel-execution and flag-forwarding behavior.
