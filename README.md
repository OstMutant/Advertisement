# 🧠 Advertisement Platform

A declarative, type-safe backend framework for managing advertisement entities with full control over SQL filtering, DTO mapping, and repository logic. Designed to replace boilerplate-heavy JPA/Hibernate with transparent, maintainable abstractions.

## 🚀 Project Philosophy

This project is built on principles of architectural clarity and full control:

- ❌ No magic
- ❌ No boilerplate
- ✅ Full transparency across all layers
- ✅ Explicit SQL, DTO, and filter logic
- ✅ Scalable abstractions for any entity

Every repository is a composition of declarative mappings and reusable filter rules. Every DTO is immutable and purpose-driven. Every SQL query is generated with precision.

## 📦 Project Structure

src/  
├── main/  
│   ├── java/org/ost/advertisement/  
│   │   ├── domain/                # Domain models (e.g., User, Role)  
│   │   ├── dto/                   # Filter DTOs for querying  
│   │   ├── repository/            # Core SQL framework (RepositoryCustom, FieldRelations, etc.)  
│   │   ├── repository/user/       # User-specific repository logic and mappers  
│   │   ├── user/                  # UserId marker interface  
│   └── resources/  
│       └── application.yml        # Spring Boot configuration  
├── test/  
│   └── java/org/ost/advertisement/ # (To be added) Unit and integration tests

## ⚙️ Getting Started

### Prerequisites

- Java 21
- Maven
- PostgreSQL

### Run locally

git clone https://github.com/OstMutant/Advertisement.git  
cd Advertisement  
./mvnw spring-boot:run

### Sample application.yml

spring:  
datasource:  
url: jdbc:postgresql://localhost:5432/advertisement  
username: postgres  
password: password  
sql:  
init:  
mode: always

## 🧪 Testing Recommendations

- Unit test FieldConditionsRules for SQL generation
- Integration test UserRepositoryCustomImpl with real DB
- Validate sorting, pagination, and edge cases

## 📌 TODO

- Add locale-based filtering
- Introduce DSL for condition composition
- Add test coverage for all repository methods
- Document framework usage for other entities

## 🛠️ Tech Stack

- Java 21
- Spring JDBC
- PostgreSQL
- Maven
- Docker (optional)

## 👤 Author

Designed and maintained by Ostap — declarative architect, refactoring perfectionist, and legacy liberator.
