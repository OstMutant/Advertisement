# Sources: pluggable-modules-opaque-refs

Purpose: every source the planner used, written with add-source.sh.
Level: 0 is a direct source, 1 and 2 are reached by following references when sources conflict.

| link | taken | confirms or contradicts | date | type | level | confidence |
|---|---|---|---|---|---|---|
| https://www.postgresql.org/docs/release/14.0/ | PG14 added hash lookup for IN/ANY with many constants; large ANY arrays no longer scan the list sequentially | neutral | 2026-11-09 | official-doc | 0 | high |
| https://docs.spring.io/spring-modulith/reference/fundamentals.html | Modules interact through provided interfaces (beans, events); internal code must not be referenced from other modules | confirms | 2026-11-09 | official-doc | 0 | high |
| https://docs.spring.io/spring-modulith/reference/runtime.html | Modules define migrations for their own persistent data only, run in module dependency order (each module its own history) | confirms | 2026-11-09 | official-doc | 0 | high |
| https://milanjovanovic.tech/blog/modular-monolith-data-isolation | Joins only between tables of the same module; other data through the module's public API; schema-per-module as starting point | confirms | 2026-11-09 | blog | 0 | medium |
| https://www.informit.com/articles/article.aspx?p=2020371 | Implementing DDD ch.10 has the rule 'Reference Other Aggregates by Identity' (heading verified, body text not reachable) | confirms | 2026-11-09 | other | 0 | medium |
| https://docs.spring.io/spring-boot/api/java/org/springframework/boot/test/context/FilteredClassLoader.html | FilteredClassLoader hides classes, packages or resources so a context runner can test an absent module | neutral | 2026-11-09 | official-doc | 0 | high |
| https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html | ApplicationContextRunner builds one context per module combination; FilteredClassLoader simulates an absent module; @ConditionalOnMissingBean for consumer-declared defaults | confirms | 2026-11-09 | official-doc | 0 | high |
| https://www.postgresql.org/docs/current/using-explain.html | EXPLAIN ANALYZE/BUFFERS: Planning Time is separate from Execution Time; used to read the measured plans | neutral | 2026-11-09 | official-doc | 0 | high |
| https://use-the-index-luke.com/no-offset | OFFSET pagination cost grows with depth regardless of filter shape; affects both variants equally (S5 run) | neutral | 2026-11-09 | blog | 0 | medium |
