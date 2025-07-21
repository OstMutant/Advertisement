FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app
COPY . .

RUN chmod +x mvnw

RUN ./mvnw clean package "-Dspring.profiles.active=prod" "-Dvaadin.productionMode=true"

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Dspring.profiles.active=prod", "-jar", "app.jar"]
