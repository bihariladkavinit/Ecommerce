Let me check the user-service port and context path to give you accurate answers.


1 tool call
Read File

application.yml
Good, I have everything I need. Let me answer all three questions directly.

1. Why http://localhost:8080/ shows nothing
The gateway has no route mapped to /. It only routes specific API paths:

/api/v1/auth/**
/api/v1/users/**
/api/v1/products/**
/api/v1/categories/**
/api/v1/inventory/**
/api/v1/cart/**
/api/v1/orders/**
/api/v1/shipments/**
A bare GET / matches none of those, so the gateway returns 404. This is correct behaviour — the gateway is not a web server serving pages. Try http://localhost:8080/actuator/health instead; if the gateway is running you'll get {"status":"UP"}.

2. Services you need running to register a user
Minimum required stack, in startup order:

#	Service	Why needed	Port
1	PostgreSQL	user-service stores users here (user_db)	5432
2	Redis	user-service stores refresh tokens; gateway checks JWT blacklist	6379
3	Eureka Server	gateway resolves lb://user-service by looking up Eureka; user-service registers here	8761
4	User Service	handles the register/login logic	8081
5	API Gateway	the public entry point routing to user-service	8080
Kafka is not needed for registration — user-service already excludes Kafka auto-config. Payment, Inventory, Cart, Order, Shipment, Notification are all irrelevant for this flow.

Quickest way to start infra locally (from the api , db and kafka contracts folder):

bash

docker compose up postgres redis eureka-server -d
Then start user-service and api-gateway from IntelliJ.

3. Register a user — curl
The full path is:

Gateway: POST http://localhost:8080/api/v1/auth/register
Gateway routes /api/v1/auth/** → lb://user-service
User-service has context-path /api/v1, so it receives POST /auth/register
bash

curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "john.doe@example.com",
    "password": "SecurePass123!",
    "firstName": "John",
    "lastName": "Doe"
  }'
Expected response 201 Created:

json

{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "email": "john.doe@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "roles": ["ROLE_USER"]
}
Conditions that must hold:

Content-Type: application/json is required — user-service validates this
email must be unique — a second register with the same email returns 409 Conflict
password validation rules apply (check RegisterRequest @Valid constraints in user-service)
No Authorization header needed — /api/v1/auth/** is public in both the gateway filter and Spring Security config
All 5 services in the table above must be running and healthy
Follow-up login curl (once registered):

bash

curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "john.doe@example.com",
    "password": "SecurePass123!"
  }'
Returns a TokenResponse with the accessToken you'd pass as Authorization: Bearer <token> on all subsequent authenticated calls.