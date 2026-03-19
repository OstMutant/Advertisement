FROM eclipse-temurin:25-jdk AS builder
WORKDIR /app

COPY pom.xml mvnw lombok.config ./
COPY .mvn .mvn

RUN chmod +x mvnw && sed -i 's/\r//' mvnw
RUN ./mvnw dependency:go-offline -q

COPY src ./src

RUN ./mvnw clean package -Pproduction -DskipTests -q

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Dspring.profiles.active=prod", "-jar", "app.jar"]