# Advertisement Platform
Backend Architecture Playground & Engineering Showcase

## About the Project

Advertisement Platform is an evolving backend system developed as an architecture playground and engineering showcase.

The primary goal of this project is not feature completeness, but:
- exploring architectural trade-offs,
- experimenting with backend patterns,
- demonstrating full control over data flow and system complexity.

There is intentionally no fixed final product vision.  
The project is designed to evolve as ideas, experiments, and architectural directions change.

---

## Engineering Goals

- Design a maintainable and extensible backend architecture
- Avoid hidden framework behavior and implicit magic
- Prefer explicit, type-safe, and transparent solutions
- Explore alternative approaches to common backend problems
- Demonstrate senior-level backend engineering mindset

---

## Architectural Philosophy

Explicit over implicit:
- No ORM, no JPA
- All SQL queries are written manually using Spring JDBC
- No hidden query generation or side effects

Controlled complexity:
- Generic filtering built on composable FieldCondition<T> and FieldRelation<T>
- Rule-based validation isolated from UI and services
- Clear ownership of responsibilities across layers

Immutable data model:
- All DTOs are immutable
- No shared mutable state
- Predictable and safe data flow

UI as an implementation detail:
- Vaadin is used only for layout and interaction wiring
- No business logic in UI components
- Backend remains UI-agnostic and reusable

Declarative approach:
- Validation rules are expressed declaratively
- PDF layouts are described via DTOs and rendered using iText
- Localization is centralized and strongly typed

---

## Project Structure

src/
- dto/  
  Immutable data transfer objects

- filter/  
  Generic filtering logic (FieldCondition, FieldRelation)

- repository/  
  Explicit SQL repositories using Spring JDBC

- service/  
  Business logic and validation rules

- view/  
  Vaadin UI components (layout and interaction only)

- config/  
  Application and security configuration

- util/  
  Shared utilities and helpers

The structure emphasizes clarity, separation of concerns, and evolvability.

---

## Key Technical Decisions

Manual SQL  
Full control over queries and predictable performance

No ORM  
Avoid hidden behavior and implicit state

Immutable DTOs  
Safer reasoning and fewer side effects

Generic filtering  
Reusable and composable query logic

Rule-based validation  
Testable and extensible validation rules

Centralized localization  
Strongly typed UI text management

---

## Project Status

Actively evolving.

This repository represents engineering thinking in progress:
- architectural ideas may be revisited,
- solutions may be refactored or replaced,
- experiments are expected and encouraged.

Stability is secondary to learning, clarity, and architectural exploration.

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

- Expand rule-based validation framework
- Enhance generic filtering capabilities
- Add architectural decision records (ADR)
- Introduce focused integration and contract tests
- Explore alternative UI or API layers

---

## Author’s Note

This project reflects my approach to backend engineering.

I value clarity over convenience.  
I prefer explicitness over magic.  
I design systems to be understood, not just used.

Architectural discussions and feedback are welcome.
