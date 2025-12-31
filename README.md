# Advertisement Platform
Backend Architecture Playground & Engineering Showcase

## About the Project

Advertisement Platform is an evolving backend system developed as an architecture playground and engineering showcase.

The primary goal of this project is not feature completeness, but:
- exploring architectural trade-offs,
- experimenting with backend patterns,
- demonstrating explicit control over data flow and system complexity.

There is intentionally no fixed final product vision.  
The project is designed to evolve as ideas, experiments, and architectural directions change.

---

## Engineering Goals

- Design a maintainable and evolvable backend architecture
- Avoid hidden framework behavior and implicit magic
- Prefer explicit, type-safe, and transparent solutions
- Explore alternative approaches to common backend problems
- Demonstrate a senior-level backend engineering mindset

---

## Architectural Philosophy

### Explicit over implicit
- No ORM, no JPA
- All SQL queries are written manually using Spring JDBC
- No hidden query generation or implicit persistence behavior

### Controlled complexity
- Generic filtering built on composable `FieldCondition<T>` and `FieldRelation<T>`
- Validation rules separated from UI and expressed explicitly
- Clear responsibility boundaries, with some areas intentionally explored and evolving

### Immutable data flow
- DTOs are treated as immutable data carriers
- No shared mutable state between layers
- Predictable and traceable data transformations

### UI as a thin adapter
- Vaadin is used only for layout and interaction wiring
- No business decision-making inside UI components
- Backend logic remains reusable and UI-agnostic

### Declarative tendencies
- Validation rules are expressed declaratively where possible
- PDF layouts are described via DTOs and rendered using iText
- Localization is centralized and strongly typed

---

## Project Structure

src/
- dto/        Data transfer objects
- filter/     Generic filtering abstractions
- repository/ Explicit SQL repositories (Spring JDBC)
- service/    Business logic and validation orchestration
- view/       Vaadin UI components (layout only)
- config/     Application and security configuration
- util/       Shared utilities

The structure emphasizes clarity, separation of concerns, and architectural evolvability.

---

## Key Technical Decisions

Manual SQL  
Full control over queries and predictable behavior

No ORM  
Avoid implicit state, lifecycle complexity, and hidden side effects

Explicit filtering model  
Composable query logic without ORM-driven abstractions

Structured validation  
Rule-oriented validation logic, isolated from UI concerns

Centralized localization  
Strongly typed and consistent UI text management

---

## Architectural Maturity

This codebase intentionally contains areas that are not fully generalized or polished.

Some solutions are deliberately explicit and verbose to:
- expose architectural trade-offs,
- support experimentation,
- favor understandability over abstraction completeness.

Refactoring and replacement of existing solutions is considered a natural part of the project’s evolution.

---

## Project Status

Actively evolving.

This repository represents engineering thinking in progress:
- architectural decisions may be revisited,
- implementations may be replaced,
- experiments are expected and encouraged.

Stability and completeness are secondary to learning, clarity, and architectural exploration.

---

## Getting Started

git clone https://github.com/OstMutant/Advertisement.git  
cd Advertisement  
./mvnw spring-boot:run

Open: http://localhost:8080

---

## Test Data

Database schema and seed data are managed via Liquibase.

To populate test data, provide a data-test.xml changelog with full DTO coverage.

---

## Ideas and Future Exploration

- Extend rule-based validation capabilities
- Improve composability and readability of generic filtering
- Add architectural decision records (ADR)
- Introduce focused integration and contract tests
- Explore alternative API or UI adapters

---

## Author’s Note

This project reflects my approach to backend engineering.

I value clarity over convenience.  
I prefer explicitness over magic.  
I design systems to be understood, not just used.

Architectural discussions and feedback are welcome.
