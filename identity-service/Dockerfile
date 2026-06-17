# ── Stage 1: Build ───────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app

COPY pom.xml .
COPY eureka-service/pom.xml ./eureka-service/
COPY config-service/pom.xml ./config-service/
COPY api-gateway/pom.xml ./api-gateway/
COPY identity-service/pom.xml ./identity-service/
COPY product-service/pom.xml ./product-service/
COPY order-service/pom.xml ./order-service/
COPY inventory-service/pom.xml ./inventory-service/

RUN mvn dependency:go-offline -pl identity-service -am -q

COPY identity-service/src ./identity-service/src
RUN mvn package -pl identity-service -am -DskipTests -q

# ── Stage 2: Run ─────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/identity-service/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
