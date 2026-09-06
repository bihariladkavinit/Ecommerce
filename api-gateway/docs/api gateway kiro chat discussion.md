[1]: How should JWT validation work in the Gateway? a. Symmetric secret (HS256) — Gateway reads JWT_SECRET from env (already in docker-compose.yml as JWT_SECRET: ${JWT_SECRET:-change-me-dev-secret}), validates the token locally, and forwards X-User-Id / X-User-Roles headers downstream. Simplest, consistent with user-service which also receives JWT_SECRET. b. Asymmetric (RS256) — Gateway fetches the public key from User Service's JWKS endpoint to verify tokens. More production-grade but adds a runtime dependency on user-service being up. c. Other — your preference

[2]: Which Resilience4j features do you want wired in at the Gateway level? a. Circuit Breaker per route + TimeLimiter only — aligns directly with the design doc (each downstream route gets a CB that returns 503 on open); keeps the Gateway lean b. Circuit Breaker + TimeLimiter + Rate Limiter (Redis-backed) — adds the Redis token-bucket rate limiter, which is already available since Redis is a depends_on for the Gateway in docker-compose.yml c. Other — your preference

[3]: Should the Gateway also aggregate Swagger/OpenAPI docs? a. Yes — expose 
swagger-ui.html
 at the Gateway that proxies each downstream service's /v3/api-docs, as described in the design doc b. No — skip it for now; each service serves its own docs independently c. Other — your preference

1=a,2 =a, 3 =b



Project structure:
api-gateway/
├── pom.xml
├── Dockerfile
└── src/
    ├── main/
    │   ├── java/com/ecomm/gateway/
    │   │   ├── ApiGatewayApplication.java
    │   │   ├── config/
    │   │   │   └── SecurityConfig.java
    │   │   ├── filter/
    │   │   │   └── JwtAuthenticationFilter.java
    │   │   ├── fallback/
    │   │   │   └── FallbackController.java
    │   │   └── util/
    │   │       └── JwtUtil.java
    │   └── resources/
    │       └── application.yml
    └── test/
        └── java/com/ecomm/gateway/
            ├── ApiGatewayApplicationTests.java
            └── filter/
                └── JwtAuthenticationFilterTest.java



Task Breakdown:

Task 1: Create pom.xml

Objective: Establish a valid standalone Maven project with all required dependencies.
groupId: com.ecomm, artifactId: api-gateway, Spring Boot 3.2.5, Java 21, Spring Cloud 2023.0.1
Dependencies:
spring-cloud-starter-gateway (pulls in WebFlux)
spring-cloud-starter-netflix-eureka-client
spring-cloud-starter-circuitbreaker-reactor-resilience4j
spring-boot-starter-actuator
spring-boot-starter-security
spring-boot-starter-data-redis-reactive (for JWT blacklist check)
jjwt-api, jjwt-impl, jjwt-jackson at version 0.12.6
spring-boot-starter-test + reactor-test (test scope)
dependencyManagement importing spring-cloud-dependencies BOM
Demo: mvn validate passes cleanly.
Task 2: Write ApiGatewayApplication.java and application.yml

Objective: Bootstrap the app and define all routes, CB config, and Eureka registration.
ApiGatewayApplication.java: @SpringBootApplication only — no @EnableEurekaServer, no @EnableDiscoveryClient needed (auto-configured by the Eureka client starter).
application.yml covers:
server.port: 8080
spring.application.name: api-gateway
Eureka client config reading from ${EUREKA_CLIENT_SERVICEURL_DEFAULTZONE}
Redis host from ${SPRING_DATA_REDIS_HOST:localhost}
Route table (7 routes matching the gateway route table in api-contracts.md):

/api/v1/auth/**           → lb://user-service
/api/v1/users/**          → lb://user-service
/api/v1/products/**       → lb://product-service
/api/v1/categories/**     → lb://product-service
/api/v1/inventory/**      → lb://inventory-service
/api/v1/cart/**           → lb://cart-service
/api/v1/orders/**         → lb://order-service
/api/v1/shipments/**      → lb://shipment-service
Each route gets a CircuitBreaker filter: name: <service>CB, fallbackUri: forward:/fallback
Resilience4j config under resilience4j.circuitbreaker and resilience4j.timelimiter (2s timeout per route)
Actuator: expose health, info
Demo: mvn spring-boot:run starts on port 8080 and /actuator/health returns UP.
Task 3: Write JwtUtil.java

Objective: Encapsulate all JJWT token parsing logic in one testable utility class.
JwtUtil is a @Component reading ${jwt.secret} (bound from JWT_SECRET env via relaxed binding)
Methods:
Claims extractAllClaims(String token) — parses and validates signature + expiry, throws JwtException on any failure
String extractUserId(String token)
String extractRoles(String token)
String extractJti(String token)
boolean isTokenValid(String token) — returns false instead of throwing, for filter use
Demo: Unit tests in JwtAuthenticationFilterTest exercise this class directly with a known test secret.
Task 4: Write JwtAuthenticationFilter.java

Objective: Implement the core security gate — validate JWT on every non-public request, forward trusted headers downstream.
JwtAuthenticationFilter implements GlobalFilter, Ordered (order -1 to run before routing)
Public path list: /api/v1/auth/, /api/v1/products/ (GET only), /api/v1/categories/ (GET only), /api/v1/shipments/track/, /actuator/
Filter logic:
If path matches a public route → chain.filter(exchange) immediately
Extract Authorization: Bearer <token> header; if missing → 401
Call jwtUtil.isTokenValid(token); if false → 401
Check Redis: EXISTS blacklist:{jti} via ReactiveRedisTemplate<String, String>; if present → 401 (token revoked / logged out)
Mutate request: strip Authorization header, add X-User-Id and X-User-Roles headers
chain.filter(mutatedExchange)
All 401 responses use the standard error envelope JSON shape from the design doc
Demo: Integration-style unit test with a MockServerWebExchange verifying that a valid token passes through and an invalid/missing token gets 401.
Task 5: Write SecurityConfig.java and FallbackController.java

Objective: Wire reactive Spring Security as a second defence layer, and provide the CB fallback endpoint.
SecurityConfig.java:
@EnableWebFluxSecurity
SecurityWebFilterChain bean: permits /api/v1/auth/**, /api/v1/products/**, /api/v1/categories/**, /api/v1/shipments/track/**, /actuator/**, /fallback — all others require authentication
Disable CSRF (stateless JWT API), disable form login, disable HTTP basic
FallbackController.java:
@RestController with GET/POST /fallback
Returns ResponseEntity with the standard error envelope: 503 SERVICE_UNAVAILABLE, message "Service temporarily unavailable. Please try again later.", and the current traceId from ServerWebExchange attributes if present
Demo: Hit a route with Eureka down — Gateway returns a well-formed 503 JSON instead of a raw Spring error page.
Task 6: Write tests

Objective: Provide a context smoke test and unit tests for the JWT filter.
ApiGatewayApplicationTests.java: @SpringBootTest context load test (requires JWT_SECRET property set via @TestPropertySource)
JwtAuthenticationFilterTest.java:
Unit tests using MockServerWebExchange (no Spring context needed)
Test cases: missing Authorization header → 401; malformed token → 401; expired token → 401; valid token, not blacklisted → passes through with X-User-Id header set; valid token, blacklisted in Redis → 401
Demo: mvn test passes all tests.
Task 7: Write the multi-stage Dockerfile

Objective: Produce a self-contained image matching the eureka-server Dockerfile pattern.
Stage 1 (builder): maven:3.9.6-eclipse-temurin-21 — dependency cache layer, then mvn package -DskipTests
Stage 2 (runtime): eclipse-temurin:21-jre-alpine — non-root user, COPY JAR, EXPOSE 8080, same JVM flags (UseContainerSupport, MaxRAMPercentage=75.0, urandom)
Demo: docker build -t api-gateway . completes; docker run -e JWT_SECRET=test -e EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://localhost:8761/eureka api-gateway starts without crash.


A few things worth knowing before you run it:

The JWT_SECRET env var must be at least 32 characters for JJWT's HS256 key derivation to accept it. The docker-compose.yml default (change-me-dev-secret) is exactly 22 chars — you'll want to change that to something longer (e.g. a 64-char random string) before first boot.
Spring Cloud Gateway is WebFlux-based, which means spring-boot-starter-web must never be added to this project's classpath — doing so will cause a startup conflict.
The JwtAuthenticationFilterTest deliberately avoids a Spring context, so it's fast and runs cleanly with just mvn test — no Redis or Eureka needed.

QQQQQQQQQQQQuestion  will this be completable with User service check and let me know if anything else needed to be modified
Only one file needed updating — the default JWT secret in 
application.yml
 was aligned with user-service's default. Everything else was already correct.




