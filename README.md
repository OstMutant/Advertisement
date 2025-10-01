# 🧠 Advertisement Platform

A declarative, type-safe backend framework for managing advertisement entities with full control over SQL filtering, DTO mapping, and repository logic. Designed to replace boilerplate-heavy JPA/Hibernate with transparent, maintainable abstractions.

---

## 🚀 Key Features

- ✅ **Custom SQL Filtering Framework** — built on `RepositoryCustom<T, F>` with explicit control over WHERE, ORDER BY, and pagination
- 🧩 **DTO-driven architecture** — no magic, no annotations, just clean mappings
- 🔍 **Type-safe field relations** — SQL ↔ DTO mapping via enums
- 🧪 **Composable filter rules** — reusable, declarative conditions for any entity
- 🛡️ **Immutable DTOs** — designed for safety, clarity, and testability

---

## 📁 Project Structure

src/ 
├── main/ 
│ ├── java/org/ost/advertisement/ 
│ │ ├── entities/ # Domain models (e.g. User, Role) 
│ │ ├── dto/filter/ # Filter DTOs for querying 
│ │ ├── repository/ # Core SQL framework 
│ │ ├── repository/user/ # User-specific repository logic 
│ │ └── security/ # UserId marker interface 
│ └── resources/ 
│ └── application.yml # Configuration


---

## 🧠 Core Concepts

### `RepositoryCustom<T, F>`
A generic base class for building SQL repositories with:

- `FieldRelations<T>` — maps DTO fields to SQL columns
- `FieldConditionsRules<F>` — generates SQL WHERE clauses from filters
- `RowMapper<T>` — explicit mapping from `ResultSet` to DTO

### `UserRepositoryCustomImpl`
Custom repository for `User` entity with:

- `findByFilter(UserFilter, Pageable)`
- `countByFilter(UserFilter)`
- `findByEmail(String)`

---

## 🔍 Usage Example

```java
UserFilter filter = new UserFilter();
filter.setRole(Role.ADMIN);
filter.setEmail("admin@example.com");

Pageable pageable = PageRequest.of(0, 10, Sort.by("createdAt").descending());

List<User> users = userRepository.findByFilter(filter, pageable);

