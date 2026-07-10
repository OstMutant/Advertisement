# syntax=docker/dockerfile:1
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /app

# Copy the Maven wrapper and project configuration
COPY pom.xml mvnw lombok.config ./
COPY .mvn .mvn

# Copy module POMs to leverage Docker cache for dependencies
COPY query-lib/pom.xml query-lib/
COPY platform-commons/pom.xml platform-commons/
COPY audit-spring-boot-starter/pom.xml audit-spring-boot-starter/
COPY attachment-spring-boot-starter/pom.xml attachment-spring-boot-starter/
COPY user-spring-boot-starter/pom.xml user-spring-boot-starter/
COPY advertisement-spring-boot-starter/pom.xml advertisement-spring-boot-starter/
COPY taxon-spring-boot-starter/pom.xml taxon-spring-boot-starter/
COPY marketplace-app/pom.xml marketplace-app/

# Download dependencies (this layer is cached until pom.xml changes)
RUN chmod +x mvnw && sed -i 's/\r//' mvnw
RUN --mount=type=cache,target=/root/.m2 ./mvnw dependency:go-offline -q

# Copy the source code for all modules
COPY query-lib/src ./query-lib/src
COPY platform-commons/src ./platform-commons/src
COPY audit-spring-boot-starter/src ./audit-spring-boot-starter/src
COPY attachment-spring-boot-starter/src ./attachment-spring-boot-starter/src
COPY user-spring-boot-starter/src ./user-spring-boot-starter/src
COPY advertisement-spring-boot-starter/src ./advertisement-spring-boot-starter/src
COPY taxon-spring-boot-starter/src ./taxon-spring-boot-starter/src
COPY marketplace-app/src ./marketplace-app/src

# Install parent POM and all dependency modules to local Maven repo before building marketplace-app
RUN --mount=type=cache,target=/root/.m2 ./mvnw install -DskipTests -pl .,platform-commons,query-lib,audit-spring-boot-starter,attachment-spring-boot-starter,user-spring-boot-starter,advertisement-spring-boot-starter,taxon-spring-boot-starter -q

# Build the application (Vaadin production bundle is built automatically by
# vaadin-maven-plugin on `package`; SPRING_PROFILES_ACTIVE=prod at runtime sets
# vaadin.productionMode=true, see application-prod.yml). Cache the Node.js
# runtime download (/root/.vaadin) only — a node_modules cache mount was tried
# and reverted: vaadin-maven-plugin rm-rf's and recreates that directory on
# every build, which a cache mount's fixed mountpoint can't survive, so it
# silently reinstalled from scratch anyway while logging a spurious error.
RUN --mount=type=cache,target=/root/.m2 \
    --mount=type=cache,target=/root/.vaadin \
    ./mvnw package -DskipTests -pl marketplace-app -q

FROM eclipse-temurin:25-jre
WORKDIR /app

# Copy the generated executable JAR from the application module
COPY --from=builder /app/marketplace-app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
