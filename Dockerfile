FROM eclipse-temurin:25-jdk AS builder
WORKDIR /app

# Copy the Maven wrapper and project configuration
COPY pom.xml mvnw lombok.config ./
COPY .mvn .mvn

# Copy module POMs to leverage Docker cache for dependencies
COPY query-starter/pom.xml query-starter/
COPY platform-commons/pom.xml platform-commons/
COPY audit-spring-boot-starter/pom.xml audit-spring-boot-starter/
COPY attachment-spring-boot-starter/pom.xml attachment-spring-boot-starter/
COPY marketplace-app/pom.xml marketplace-app/

# Download dependencies (this layer is cached until pom.xml changes)
RUN chmod +x mvnw && sed -i 's/\r//' mvnw
RUN ./mvnw dependency:go-offline -q

# Copy the source code for all modules
COPY query-starter/src ./query-starter/src
COPY platform-commons/src ./platform-commons/src
COPY audit-spring-boot-starter/src ./audit-spring-boot-starter/src
COPY attachment-spring-boot-starter/src ./attachment-spring-boot-starter/src
COPY marketplace-app/src ./marketplace-app/src

# Build the project (Vaadin production mode enabled via POM profile)
RUN ./mvnw clean package -Pproduction -DskipTests -q

FROM eclipse-temurin:25-jre
WORKDIR /app

# Copy the generated executable JAR from the application module
COPY --from=builder /app/marketplace-app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
