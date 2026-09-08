# ── Stage 1: build ────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy Maven wrapper and pom first for layer caching
COPY pom.xml .
COPY src ./src

# Build without tests (tests run in CI, not during image build)
RUN apk add --no-cache maven && mvn clean package -DskipTests

# ── Stage 2: runtime ──────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy only the fat JAR from the build stage
COPY --from=builder /app/target/cart-service-0.0.1-SNAPSHOT.jar app.jar

# Non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

EXPOSE 8084

ENTRYPOINT ["java", "-jar", "app.jar"]
