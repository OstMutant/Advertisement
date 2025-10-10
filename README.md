# Advertisement Platform

This backend platform manages advertisement campaigns using a declarative, type-safe architecture focused on full control, maintainability, and zero boilerplate. No magic, no hidden behavior—just explicit logic designed for scalability and clarity.

## Architectural Philosophy

- Immutable DTOs: All data objects are constructed via builders or explicit constructors. No mutable state.
- Generic Filtering: Powered by FieldCondition<T> and FieldRelation<T> interfaces for universal DTO filtering.
- Manual SQL: No JPA or ORM. All queries are handcrafted using Spring JDBC for full transparency.
- Explicit Mapping: All RowMapper implementations are written manually. No framework-generated mappers.
- UI Without Logic: Vaadin views contain only layout and interaction wiring. All business logic lives in services.
- Rule-Based Validation: Validation is declarative, testable, and decoupled from UI using custom Validator<T> interfaces.
- Centralized Localization: All UI texts are managed via a LocaleKey enum and resolved through a LocaleService.
- Declarative PDF Generation: Layouts are described via DTOs and rendered using iText without imperative code.

## Project Structure

src/
├── dto/           # Immutable data transfer objects
├── filter/        # Generic filtering logic with FieldCondition and FieldRelation
├── repository/    # Explicit SQL repositories using Spring JDBC
├── service/       # Business logic and validation
├── view/          # Vaadin-based UI components (no business logic)
├── config/        # Security and application configuration
├── util/          # Utility classes and helpers

## Getting Started

1. Clone the repository:
   git clone https://github.com/OstMutant/Advertisement.git
   cd Advertisement
   git checkout feature/refactoring

2. Run the application:
   ./mvnw spring-boot:run

3. Open http://localhost:8080 in your browser.

## Test Data

Liquibase is used for schema and data migration. To populate test data, add a data-test.xml changelog with full DTO coverage.

## TODO / Ideas

- Introduce Validator<T> interfaces for rule-based validation.
- Implement LocaleService with centralized key management.
- Refactor PDF generation into declarative DTO-driven layout.
- Add integration tests with full SQL coverage.

## Author

Designed with a passion for clean architecture, type safety, and total control over every layer of the stack.
