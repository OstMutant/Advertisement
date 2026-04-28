FROM eclipse-temurin:25-jdk AS builder
WORKDIR /app

# Copy the Maven wrapper and project configuration
COPY pom.xml mvnw lombok.config ./
COPY .mvn .mvn

# Copy module POMs to leverage Docker cache for dependencies
COPY sql-engine/pom.xml sql-engine/
COPY advertisement-events/pom.xml advertisement-events/
COPY attachment-spring-boot-starter/pom.xml attachment-spring-boot-starter/
COPY advertisement-app/pom.xml advertisement-app/
COPY storage-api/pom.xml storage-api/
COPY storage-s3-spring-boot-starter/pom.xml storage-s3-spring-boot-starter/

# Download dependencies (this layer is cached until pom.xml changes)
RUN chmod +x mvnw && sed -i 's/\r//' mvnw
RUN ./mvnw dependency:go-offline -q

# Copy the source code for all modules
COPY sql-engine/src ./sql-engine/src
COPY advertisement-events/src ./advertisement-events/src
COPY attachment-spring-boot-starter/src ./attachment-spring-boot-starter/src
COPY advertisement-app/src ./advertisement-app/src
COPY storage-api/src ./storage-api/src
COPY storage-s3-spring-boot-starter/src ./storage-s3-spring-boot-starter/src

# Build the project (Vaadin production mode enabled via POM profile)
RUN ./mvnw clean package -Pproduction -DskipTests -q

FROM eclipse-temurin:25-jre
WORKDIR /app

# Copy the generated executable JAR from the application module
COPY --from=builder /app/advertisement-app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]