# advertisement-spring-boot-starter

Auto-configured Advertisement domain for the Advertisement Platform.

## What it provides

- Advertisement CRUD with ownership checks and dynamic filter/sort
- **SPI implementation:** `AdvertisementPort` (called by marketplace-app)

## Key classes

| Class | Role |
|---|---|
| `AdvertisementPortImpl` | Entry point — implements `AdvertisementPort`, delegates to `AdvertisementService` |
| `AdvertisementService` | Create, update, delete, ownership validation |
| `AdvertisementRepository` | Persists and queries `advertisement`; supports dynamic filter/sort |

## Dependencies

- `platform-commons` — `AdvertisementPort` SPI and DTOs
- `query-lib` — `SqlFilterBuilder`, `OrderByBuilder` for dynamic queries
- Spring Boot, Spring JDBC, Liquibase
