FROM eclipse-temurin:25-jdk AS builder
WORKDIR /app

# Copy the Maven wrapper and project configuration
COPY pom.xml mvnw lombok.config ./
COPY .mvn .mvn

# Copy module POMs to leverage Docker cache for dependencies
COPY sql-engine/pom.xml sql-engine/
COPY advertisement-app/pom.xml advertisement-app/

# Download dependencies (this layer is cached until pom.xml changes)
RUN chmod +x mvnw && sed -i 's/\r//' mvnw
RUN ./mvnw dependency:go-offline -q

# Copy the source code for both modules
COPY sql-engine/src ./sql-engine/src
COPY advertisement-app/src ./advertisement-app/src

# Build the project (Vaadin production mode enabled via POM profile)
RUN ./mvnw clean package -Pproduction -DskipTests -q

FROM eclipse-temurin:25-jre
WORKDIR /app

# Copy the generated executable JAR from the application module
COPY --from=builder /app/advertisement-app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-Dspring.profiles.active=prod", "-jar", "app.jar"]