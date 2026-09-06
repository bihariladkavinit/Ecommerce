# ── Stage 1: build ────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-21 AS builder

WORKDIR /build

# Cache the dependency layer separately from source so re-builds are fast
# when only source files change
COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn package -DskipTests -q

# ── Stage 2: runtime ──────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Run as non-root for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /build/target/eureka-server-*.jar app.jar

EXPOSE 8761

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
