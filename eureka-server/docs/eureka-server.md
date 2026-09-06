can you give me plan to create eureka server based on the attached documents

1]: Where will the Eureka Server live in your project structure? a. Separate Maven module inside a Maven multi-module parent project (e.g. 
pom.xml
 with a parent pom.xml at ecommerce/) b. Standalone Maven project — its own independent pom.xml, no parent module, just like a regular Spring Boot app c. Other — provide your own preference

[2]: Do you want the Eureka Server to run in standalone/single-node mode (no peer replication)? a. Yes, single node — simplest for local dev and Docker Compose; fine for this portfolio project b. Peer-aware replication — multiple Eureka instances that replicate to each other (more complex, closer to production HA setup) c. Other — your own preference

[3]: What should the Dockerfile for the Eureka Server look like? a. Multi-stage build — stage 1 compiles with Maven, stage 2 runs the JAR on a slim JRE image (self-contained, no local Maven needed) b. Single-stage, pre-built JAR — assumes you run mvn package locally first, then COPY target/*.jar into the image c. Other — e.g. Jib plugin, layered JARs, etc.

1=b , 2 = a , 3=a

eureka-server/
├── pom.xml
├── Dockerfile
└── src/
    └── main/
        ├── java/com/ecomm/eurekaserver/
        │   └── EurekaServerApplication.java
        └── resources/
            └── application.yml
    └── test/
        └── java/com/ecomm/eurekaserver/
            └── EurekaServerApplicationTests.java


Task Breakdown:

Task 1: Create the Maven project structure and pom.xml

Objective: Set up a valid standalone Maven project for the Eureka Server.
Create 
pom.xml
 with:
Parent: spring-boot-starter-parent 3.2.5
groupId: com.ecomm, artifactId: eureka-server, version: 0.0.1-SNAPSHOT
java.version: 21, spring-cloud.version: 2023.0.1
Dependencies: spring-cloud-starter-netflix-eureka-server, spring-boot-starter-actuator
dependencyManagement importing spring-cloud-dependencies BOM
spring-boot-maven-plugin in <build>
Test dependency: spring-boot-starter-test
Demo: mvn validate runs without errors from within eureka-server/.
Task 2: Write the main application class

Objective: Bootstrap a Spring Boot app that activates the Eureka Server auto-configuration.
Create 
EurekaServerApplication.java
Annotated with @SpringBootApplication and @EnableEurekaServer
Standard SpringApplication.run(...) main method
Demo: The class compiles cleanly (mvn compile).
Task 3: Configure application.yml for standalone single-node mode

Objective: Configure Eureka to run as a standalone registry (no self-registration, no peer fetching) and expose Actuator.
Create 
application.yml
 with:
yaml

spring:
  application:
    name: eureka-server

server:
  port: 8761

eureka:
  instance:
    hostname: localhost
  client:
    register-with-eureka: false   # don't self-register
    fetch-registry: false         # don't fetch peer registry
    service-url:
      defaultZone: http://${eureka.instance.hostname}:${server.port}/eureka/
  server:
    wait-time-in-ms-when-sync-empty: 0   # start accepting registrations immediately

management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: always
Demo: mvn spring-boot:run starts successfully, dashboard is accessible at http://localhost:8761, and http://localhost:8761/actuator/health returns {"status":"UP"}.
Task 4: Write the application context smoke test

Objective: Ensure the Spring context loads correctly as a fast CI gate.
Create 
EurekaServerApplicationTests.java
Single @SpringBootTest test class with a contextLoads() method (empty body — context load failure will fail the test automatically)
Demo: mvn test passes with the context loading cleanly.
Task 5: Write the multi-stage Dockerfile

Objective: Produce a self-contained, production-style image that mirrors the user-service Dockerfile pattern.
Create eureka-server/Dockerfile:
Stage 1 (builder): maven:3.9.6-eclipse-temurin-21 — copy pom.xml, run mvn dependency:go-offline, copy src/, run mvn package -DskipTests
Stage 2 (runtime): eclipse-temurin:21-jre-alpine — non-root user (appgroup/appuser), copy JAR from builder, EXPOSE 8761, ENTRYPOINT with -XX:+UseContainerSupport, -XX:MaxRAMPercentage=75.0, -Djava.security.egd=file:/dev/./urandom
Demo: docker build -t eureka-server . completes successfully from within eureka-server/.
Task 6: Wire Eureka Server into docker-compose.yml and do an end-to-end smoke test

Objective: Confirm the Eureka Server container starts healthy and is reachable by other services on ecommerce-net.
Add a healthcheck to the eureka-server service in docker-compose.yml (the entry already exists — just add the healthcheck block):
yaml

healthcheck:
  test: ["CMD-SHELL", "wget -qO- http://localhost:8761/actuator/health | grep UP || exit 1"]
  interval: 10s
  timeout: 5s
  retries: 10
Update any downstream service depends_on entries (where you want to guarantee ordering) from condition: service_started to condition: service_healthy for eureka-server.
Demo: docker compose up eureka-server brings up the container, the healthcheck turns green, and http://localhost:8761 shows the Eureka dashboard with "No instances currently registered".



Updated file:

docker-compose.yml — added healthcheck to eureka-server (uses wget on /actuator/health, start_period: 30s to give the JVM time to boot), and upgraded all 8 downstream services from condition: service_started to condition: service_healthy so they only start once Eureka is actually accepting registrations.
Two things worth noting for when you run it:

The enable-self-preservation: false in application.yml means Eureka will aggressively evict stale instances — that's the right call for local dev but you'd flip it back to true in production.
The first docker compose up --build will be slow because the builder stage downloads the internet. Subsequent builds hit the dependency cache layer.



