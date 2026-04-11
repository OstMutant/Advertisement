# Advertisement Project Architecture Rules

## Core Stack
- Java 25 (Use modern features: Records, Pattern Matching, Switch expressions).
- Spring Boot (Web, Security, Data JDBC).
- Pure SQL via `NamedParameterJdbcTemplate` (NO JPA, NO HIBERNATE).
- Vaadin for UI.

## Architecture Guidelines
1. **Explicit over implicit:** Avoid hidden framework magic. If simple Java code works, use it.
2. **Strict Boundaries:** The UI layer MUST NOT call Repositories directly. Always use `UserService` or `AdvertisementService`.
3. **Modular Storage:** We use a strict modular structure. `storage-api` is the contract, `storage-s3-spring-boot-starter` is the implementation. UI components (like `AttachmentGallery`) MUST degrade gracefully (using `ObjectProvider.ifAvailable()`) if `storage.s3.enabled=false`.
4. **Validation:** Use declarative validation rules in DTOs.
5. **Database Changes:** Database schema must ONLY be modified via Liquibase scripts in `db/changelog/changes`.

When writing code or refactoring, strictly respect these boundaries. Think about which module a feature belongs to before implementing it.