# Kafka + Spring Boot + PostgreSQL Microservices Demo

A complete, runnable example of event-driven microservices using **Spring Boot 3.2**, **Spring Cloud
Gateway**, **Netflix Eureka**, **Spring Security + JWT**, **Spring Kafka**, **PostgreSQL**, and **Docker
Compose**. This models a simplified telecom/e-commerce order flow behind a single API gateway, with
service discovery so nothing hardcodes anyone else's host/port:

```
                                      ┌─────────────────────┐
                                      │  discovery-service   │  (Eureka registry, 8761)
                                      │  every service below │
                                      │  registers itself    │
                                      └─────────▲────────────┘
                                                │ registers / heartbeats
              ┌───────────────┬─────────────────┼─────────────────┬───────────────────┐
              │               │                 │                 │                   │
        [auth-service]  [order-service]  [inventory-service]  [notification-service]   │
         authdb              orderdb        inventorydb          notificationdb        │
              ▲               ▲                                                        │
              │               │                                                        │
              └───────┬───────┴────────────────────────────────────────────────────────┘
                      │  lb://SERVICE-NAME (looked up via Eureka, load-balanced)
              ┌───────┴────────┐
Client --HTTP-│  api-gateway    │  (single entry point, 8080)
              └────────────────┘

Event flow (unchanged, still choreographed over Kafka):
auth-service --Kafka(user-events)--> notification-service
order-service --Kafka(order-events)--> inventory-service --Kafka(inventory-events)--> order-service & notification-service
```

## 1. Architecture

| Service               | Port | Database         | Responsibility                                                                 |
|-----------------------|------|-------------------|--------------------------------------------------------------------------------|
| discovery-service     | 8761 | —                 | Eureka service registry. Every other service registers itself here on startup and sends heartbeats. |
| api-gateway           | 8080 | —                 | Single public entry point. Routes `/api/auth/**`, `/api/orders/**`, `/api/products/**`, `/api/notifications/**` to the right service via Eureka-resolved, load-balanced `lb://SERVICE-NAME` URIs. Also does a coarse "is there an Authorization header at all" check on order routes, and stamps every request with a correlation ID. |
| auth-service          | 8084 | authdb            | Registers/authenticates users (BCrypt-hashed passwords), issues signed JWTs. Publishes `UserRegisteredEvent` to Kafka. |
| order-service         | 8081 | orderdb           | Requires a valid JWT. Exposes REST API to create orders. Publishes `OrderCreatedEvent` to Kafka. Consumes `InventoryEvent` to update order status. |
| inventory-service     | 8082 | inventorydb       | Consumes `OrderCreatedEvent`. Reserves stock (with pessimistic row locking). Publishes `InventoryEvent` (RESERVED/FAILED). |
| notification-service  | 8083 | notificationdb    | Consumes `InventoryEvent` AND `UserRegisteredEvent`. Simulates sending an email/SMS/WhatsApp notification and persists a log. |

**Kafka topics**
- `user-events` — produced by auth-service, consumed by notification-service
- `order-events` — produced by order-service, consumed by inventory-service
- `inventory-events` — produced by inventory-service, consumed by order-service AND notification-service (both in their own separate consumer groups, so both get every message — that's the fan-out pattern)

## 2. Gateway & service discovery design

- **discovery-service** is a plain Netflix Eureka Server (`@EnableEurekaServer`). It doesn't register with
  itself (`register-with-eureka: false`, `fetch-registry: false`) — it's purely the registry.
- Every other service (auth, order, inventory, notification, and the gateway itself) includes
  `spring-cloud-starter-netflix-eureka-client` and points at `discovery-service` via the `EUREKA_URL` env
  var. On startup each one registers under its `spring.application.name` (uppercased, e.g. `ORDER-SERVICE`)
  and sends heartbeats; visit **http://localhost:8761** to watch instances register live.
- **api-gateway** is Spring Cloud Gateway (reactive/WebFlux, not the servlet stack — hence no
  `spring-boot-starter-web` in its `pom.xml`, the two web stacks don't mix). Routes are defined
  programmatically in `GatewayRoutesConfig` using `lb://SERVICE-NAME` URIs — Spring Cloud LoadBalancer
  resolves that against whatever instances are currently registered in Eureka. **Scale a service to 3
  replicas and the gateway load-balances across all of them automatically — no gateway config change
  needed.** That's the core value of pairing a gateway with service discovery instead of hardcoding
  downstream URLs.
- The gateway does a **coarse edge check** on `/api/orders/**` (is there an `Authorization` header at
  all?) purely to fail fast without a wasted network hop — order-service still does the real JWT signature
  and expiry verification. This is intentional defense-in-depth, not duplicated logic: a compromised or
  misconfigured gateway should never be the only thing standing between a request and a protected service.
- A `GlobalFilter` (`LoggingGlobalFilter`) stamps every request with an `X-Request-Id` header and logs the
  method + path — a minimal example of the kind of cross-cutting concern (correlation IDs, rate limiting,
  circuit breaking) that belongs at the gateway rather than duplicated in every service.
- **Interview-relevant trade-off:** Eureka is an AP system in CAP terms — it favors availability, and can
  briefly serve stale/duplicate registry data during partitions rather than reject requests. In a
  Kubernetes deployment you typically wouldn't run Eureka at all — Kubernetes' built-in DNS-based service
  discovery (a `Service` object, `ClusterIP`, `my-service.namespace.svc.cluster.local`) replaces this
  entirely, and you'd more likely use an Ingress controller or a service mesh (Istio/Linkerd) instead of
  Spring Cloud Gateway for routing. Eureka + Spring Cloud Gateway is the classic pattern for VM-based or
  plain-Docker deployments, which is exactly what this demo runs on.

## 3. Authentication design

- **auth-service** stores users with a BCrypt password hash and a set of roles (`USER` by default).
  `/api/auth/register` and `/api/auth/login` both return a signed **JWT (HS256)** containing the username
  as subject and the user's roles as a claim.
- **order-service** acts as a stateless **resource server**: a custom `OncePerRequestFilter`
  (`JwtAuthenticationFilter`) reads the `Authorization: Bearer <token>` header on every request, verifies
  the signature and expiry locally (no network call back to auth-service), and populates Spring Security's
  `SecurityContext` with the username + `ROLE_*` authorities from the token.
- `POST /api/orders` derives `customerId` from the authenticated JWT subject — **not** from the request
  body — so nobody can place an order pretending to be someone else.
- `GET /api/orders` (list all) is restricted to `ROLE_ADMIN`; everything else on order-service just
  requires being authenticated. This demonstrates Spring Security's `hasRole(...)` role-based authorization.
- **Why a shared secret instead of a "real" OAuth2 server:** this keeps the demo runnable with zero extra
  infrastructure. In production you'd typically either (a) have auth-service sign with an RSA/EC private
  key and expose a public JWKS endpoint that other services verify against (no shared secret to leak), or
  (b) delegate entirely to a dedicated Authorization Server like Keycloak / Auth0 / AWS Cognito and use
  Spring's `spring-boot-starter-oauth2-resource-server` with `issuer-uri`. Both are natural next steps if
  you want to extend this.
- inventory-service and notification-service are **not** exposed with authentication here — in a real
  deployment they'd typically sit behind an API gateway / service mesh and only be reachable from other
  internal services, not directly from end users, which is why only order-service (the customer-facing
  entry point) enforces JWT auth in this demo.

**Why each service has its own copy of the event DTOs:** in a real multi-team org you would NOT share a
JAR of DTOs across services (that recreates a distributed monolith). Instead each service owns its own
copy of the contract (field-for-field identical JSON shape). The Kafka consumer config disables JSON
type headers (`spring.json.use.type.headers: false`) and pins a local `default.type`, so deserialization
never depends on the producer's Java package name.

**Consistency model:** this is deliberately built as **choreography** (services react to each other's
events, no central orchestrator) — a common interview topic vs. **orchestration** (a saga coordinator
service tells each participant what to do). Trade-off: choreography is simpler and more decoupled, but
harder to trace an end-to-end flow later — that's what `kafka-ui` (below) is for.

**Concurrency/correctness details worth knowing for an interview:**
- `inventory-service` uses a **pessimistic write lock** (`SELECT ... FOR UPDATE`) when reading a product's
  stock, so two concurrent orders for the same product can't both read stale stock and both succeed.
- `order-service`'s Kafka listener checks if the order is already in a terminal state (`CONFIRMED`/`CANCELLED`)
  before applying an update — a simple **idempotency guard** against duplicate delivery (Kafka is at-least-once).
- Producers use `acks=all` + `enable.idempotence=true` to avoid duplicate/lost writes on the broker side.
- Events are keyed by `orderId` so all messages for one order land on the same partition (preserves ordering per order).
- **Not implemented here (call this out if asked in interview):** true exactly-once processing would need
  the **transactional outbox pattern** (write the DB row + the outbox event in the same DB transaction, have
  a separate poller/CDC process publish to Kafka) instead of "save to DB, then call Kafka" as done here for simplicity.

## 4. Project layout

```
kafka-microservices-demo/
├── docker-compose.yml           # Full stack (all services + infrastructure)
├── docker-compose-infra.yml     # Infrastructure only (Kafka, Postgres, Zookeeper)
├── postgres-init/
│   └── init-databases.sh        # creates orderdb, inventorydb, notificationdb, authdb
├── scripts/
│   ├── setup-local-db.bat       # Windows: create databases locally
│   └── setup-local-db.sh        # Linux/Mac: create databases locally
├── discovery-service/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/example/discoveryservice/...
├── api-gateway/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/example/apigateway/...
├── auth-service/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/example/authservice/...
├── order-service/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/example/orderservice/...
├── inventory-service/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/example/inventoryservice/...
└── notification-service/
    ├── Dockerfile
    ├── pom.xml
    └── src/main/java/com/example/notificationservice/...
```

## 4.1 Ports Reference

| Component             | Port | Description                                      |
|-----------------------|------|--------------------------------------------------|
| **Infrastructure**    |      |                                                  |
| PostgreSQL            | 5432 | Database server                                  |
| Zookeeper             | 2181 | Kafka coordination                               |
| Kafka                 | 9092 | Message broker (localhost access)                |
| Kafka (internal)      | 29092| Message broker (Docker internal)                 |
| Kafka UI              | 8090 | Web UI for Kafka monitoring                      |
| **Microservices**     |      |                                                  |
| Discovery Service     | 8761 | Eureka registry dashboard                        |
| API Gateway           | 8080 | Main entry point for all API requests            |
| Auth Service          | 8084 | Authentication & JWT issuance                    |
| Order Service         | 8081 | Order management                                 |
| Inventory Service     | 8082 | Stock management                                 |
| Notification Service  | 8083 | Event notifications                              |

## 5. Prerequisites

### Option A: Run with Docker (recommended)
- Docker & Docker Compose (that's it — you do NOT need Java or Maven installed locally; the Dockerfiles
  build each service inside a Maven container)
- Ports free on your machine: `5432`, `9092`, `8080`, `8081`, `8082`, `8083`, `8084`, `8090`, `8761`
- (Optional) `jq` installed locally makes the curl examples below nicer, but isn't required.

### Option B: Run without Docker (local development)
- **Java 17** (JDK, not just JRE)
- **Maven 3.9+**
- **PostgreSQL 14+** running locally on port 5432
- **Apache Kafka 3.x+** running locally on port 9092 (with Zookeeper on 2181)
- OR use `docker-compose-infra.yml` for infrastructure only (see below)

## 6. Run it

### Option A: Full Docker (all services containerized)

From the `kafka-microservices-demo` folder:

```bash
docker compose up --build
```

First run will take a few minutes (downloading base images + Maven dependencies). `discovery-service`
comes up first (everything else depends on its healthcheck), then the four domain services register with
it, then `api-gateway` comes up last.

Open **http://localhost:8761** to see the Eureka dashboard — you should see `AUTH-SERVICE`,
`ORDER-SERVICE`, `INVENTORY-SERVICE`, `NOTIFICATION-SERVICE`, and `API-GATEWAY` all listed as `UP` once
everything has finished starting (give it 30-60s after the containers report started).

Open **http://localhost:8090** to see Kafka UI — you can watch messages flow through `user-events`,
`order-events`, and `inventory-events` live.

> Want to change the JWT signing secret? Set a `JWT_SECRET` environment variable before running
> `docker compose up` (it defaults to a long placeholder string otherwise) — it's read by both
> auth-service and order-service via the same env var, so you only need to set it once.

### Option B: Run without Docker (local development)

This option is useful when you want to debug/develop services in your IDE with hot-reload.

#### Step 1: Start infrastructure (Kafka, PostgreSQL, Zookeeper)

**Option B1: Use Docker for infrastructure only**
```bash
docker compose -f docker-compose-infra.yml up
```
This starts only Kafka, Zookeeper, PostgreSQL, and Kafka UI — not the microservices.

**Option B2: Install infrastructure locally**
- Install and start PostgreSQL on port 5432
- Install and start Kafka on port 9092 (with Zookeeper on 2181)
- Create databases using the setup script:
  ```bash
  # Windows
  scripts\setup-local-db.bat

  # Linux/Mac
  chmod +x scripts/setup-local-db.sh
  ./scripts/setup-local-db.sh
  ```

#### Step 2: Start services in order

Services must be started in this order due to dependencies:

```bash
# Terminal 1: Discovery Service (must start first)
cd discovery-service
mvn spring-boot:run

# Terminal 2: Auth Service (wait for Eureka to be ready at http://localhost:8761)
cd auth-service
mvn spring-boot:run

# Terminal 3: Order Service
cd order-service
mvn spring-boot:run

# Terminal 4: Inventory Service
cd inventory-service
mvn spring-boot:run

# Terminal 5: Notification Service
cd notification-service
mvn spring-boot:run

# Terminal 6: API Gateway (start last)
cd api-gateway
mvn spring-boot:run
```

Or build JARs first and run them:
```bash
# Build all services
mvn clean package -DskipTests -f discovery-service/pom.xml
mvn clean package -DskipTests -f auth-service/pom.xml
mvn clean package -DskipTests -f order-service/pom.xml
mvn clean package -DskipTests -f inventory-service/pom.xml
mvn clean package -DskipTests -f notification-service/pom.xml
mvn clean package -DskipTests -f api-gateway/pom.xml

# Run each (in separate terminals, in order)
java -jar discovery-service/target/discovery-service.jar
java -jar auth-service/target/auth-service.jar
java -jar order-service/target/order-service.jar
java -jar inventory-service/target/inventory-service.jar
java -jar notification-service/target/notification-service.jar
java -jar api-gateway/target/api-gateway.jar
```

#### Environment variables for local development

All services use sensible defaults for local development (localhost). Override if needed:

**Linux/Mac (bash):**
```bash
# Database (defaults to localhost:5432, postgres/postgres)
export DB_HOST=localhost
export DB_PORT=5432
export DB_USER=postgres
export DB_PASSWORD=postgres

# Kafka (defaults to localhost:9092)
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092

# Eureka (defaults to http://localhost:8761/eureka)
export EUREKA_URL=http://localhost:8761/eureka

# JWT Secret (must be same for auth-service and order-service)
export JWT_SECRET=your-secret-key-at-least-32-bytes-long
```

**Windows (Command Prompt):**
```cmd
set DB_HOST=localhost
set DB_PORT=5432
set DB_USER=postgres
set DB_PASSWORD=postgres
set KAFKA_BOOTSTRAP_SERVERS=localhost:9092
set EUREKA_URL=http://localhost:8761/eureka
set JWT_SECRET=your-secret-key-at-least-32-bytes-long
```

**Windows (PowerShell):**
```powershell
$env:DB_HOST="localhost"
$env:DB_PORT="5432"
$env:DB_USER="postgres"
$env:DB_PASSWORD="postgres"
$env:KAFKA_BOOTSTRAP_SERVERS="localhost:9092"
$env:EUREKA_URL="http://localhost:8761/eureka"
$env:JWT_SECRET="your-secret-key-at-least-32-bytes-long"
```

From here on, **all requests go through api-gateway on port 8080** — that's the point of adding it. The
individual services' ports (8081-8084) are still published too, so you can bypass the gateway to debug a
specific service directly if something's not routing as expected.

## 7. Test the flow

### Step 1 — Register a user (through the gateway)
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username": "zahirul", "email": "zahirul@example.com", "password": "supersecret1"}'
```
Response includes an `accessToken` (JWT). Save it:
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username": "zahirul2", "email": "zahirul2@example.com", "password": "supersecret1"}' | jq -r .accessToken)
```
This also publishes a `UserRegisteredEvent` — check it landed in notification-service:
```bash
curl http://localhost:8080/api/notifications
```

### Step 2 — Log in (alternative to using the register response directly)
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "zahirul2", "password": "supersecret1"}'
```

### Step 3 — Confirm the gateway rejects order requests with no Authorization header at all
```bash
curl -i -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"productId": 1, "quantity": 2}'
```
Expected: `401 Unauthorized` — this one is actually rejected by **api-gateway's** coarse header-presence
filter before it even reaches order-service (a malformed/expired token, on the other hand, would sail past
the gateway and get rejected by order-service's real JWT verification instead — try it by passing
`Authorization: Bearer garbage` to see the difference in the response).

### Step 4 — Check seeded stock (inventory-service seeds 3 products on startup)
```bash
curl http://localhost:8080/api/products
```
Expected: SIM Card (stock 100), 4G Router (stock 50), Fiber Modem (stock 5).

### Step 5 — Happy path: create an order with your token
```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"productId": 1, "quantity": 2}'
```
Response returns the order with `"status": "CREATED"` and `customerId` set to your JWT's username
(`zahirul2`) automatically — no `customerId` field is sent by the client.

Poll it a second later:
```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/orders/1
```
`status` should now be `CONFIRMED`. Check `curl http://localhost:8080/api/notifications` for the
resulting notification log entry.

### Step 6 — Failure path: order more than available stock (Fiber Modem only has 5 units)
```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"productId": 3, "quantity": 999}'
```
Poll that order — status becomes `CANCELLED`, and `GET /api/products` shows the Fiber Modem stock
unchanged. `GET /api/notifications` shows a FAILED entry with a reason.

### Step 7 — Role-based authorization: listing all orders is ADMIN-only
```bash
curl -i -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/orders
```
Expected: `403 Forbidden` — your token only has `ROLE_USER` (everyone registers as `USER` by default in
this demo; there's no `/register-admin` endpoint, intentionally — promoting a user to `ADMIN` would
normally be an internal/admin-only operation, not self-service). To test the admin path yourself, you can
temporarily insert a role row directly:
```bash
docker exec -it postgres psql -U postgres -d authdb \
  -c "INSERT INTO user_roles (user_id, role) SELECT id, 'ADMIN' FROM users WHERE username = 'zahirul2';"
```
Then log in again (`POST /api/auth/login`) to get a fresh token with the new role baked in, and retry
`GET /api/orders` — it should now return `200 OK` with every order.

## 8. Useful commands

### Docker commands
```bash
# Tail logs for one service
docker compose logs -f inventory-service

# Stop everything
docker compose down

# Stop AND wipe volumes (fresh Postgres data + reseed)
docker compose down -v

# Rebuild just one service after a code change
docker compose up --build order-service

# Scale a service to see client-side load balancing across replicas
docker compose up --build --scale inventory-service=3

# Start infrastructure only (for local development)
docker compose -f docker-compose-infra.yml up

# Stop infrastructure only
docker compose -f docker-compose-infra.yml down
```

### Local development commands (without Docker for services)
```bash
# Build all services
mvn clean package -DskipTests -f discovery-service/pom.xml && \
mvn clean package -DskipTests -f auth-service/pom.xml && \
mvn clean package -DskipTests -f order-service/pom.xml && \
mvn clean package -DskipTests -f inventory-service/pom.xml && \
mvn clean package -DskipTests -f notification-service/pom.xml && \
mvn clean package -DskipTests -f api-gateway/pom.xml

# Run with hot-reload (development mode)
cd discovery-service && mvn spring-boot:run

# Run from JAR (production-like)
java -jar discovery-service/target/discovery-service.jar

# Run with custom environment variables
DB_HOST=mydbserver KAFKA_BOOTSTRAP_SERVERS=mykafka:9092 java -jar auth-service/target/auth-service.jar
```

## 9. Common issues

- **"Connection refused" to Postgres/Kafka on service startup**: the `depends_on: condition: service_healthy`
  in `docker-compose.yml` should prevent this, but if you hit it anyway, just re-run `docker compose up` —
  the healthchecks here should avoid the race entirely.
- **Gateway returns 503 "Unable to find instance"**: the target service hasn't finished registering with
  Eureka yet (registration + the gateway's local registry cache both take a few seconds). Wait ~30s after
  `docker compose up` finishes and check http://localhost:8761 to confirm the instance shows as `UP`.
- **Consumer not receiving messages**: check `docker compose logs kafka` and confirm the topic exists via
  Kafka UI (http://localhost:8090). Auto-topic-creation is on, and each service also declares `NewTopic`
  beans on startup as a belt-and-suspenders measure.
- **Changed a DTO field and now deserialization fails**: remember the DTOs are duplicated per-service —
  update the field in *every* service's copy of the class.
- **401 on every order-service request**: double-check the `JWT_SECRET` env var is identical for
  auth-service and order-service (if you changed it for one, you must change it for both), and that
  you're sending `Authorization: Bearer <token>` (not just the raw token).
- **`WeakKeyException` on startup**: HS256 requires the secret to be at least 32 bytes — if you override
  `JWT_SECRET`, make sure the replacement string is at least that long.

## 10. Natural extensions if you want to go further

- Add a `payment-service` between order and inventory to make it a proper 3-step saga.
- Swap choreography for orchestration: introduce an `order-saga-orchestrator` that explicitly commands each step.
- Add the transactional outbox pattern to order-service and inventory-service for true exactly-once publish semantics.
- Add Kafka Streams or a Debezium CDC pipeline reading Postgres WAL instead of publishing from application code.
- Add Testcontainers-based integration tests spinning up real Kafka + Postgres per test class.
- Swap the shared-HMAC-secret JWT approach for RSA/EC signing + a JWKS endpoint, or delegate to Keycloak/Auth0.
- Add refresh tokens and a `/api/auth/refresh` endpoint (access tokens here are short-lived only via `expiration-ms`).
- Add a circuit breaker (Resilience4j) around gateway routes so a slow/down service degrades gracefully instead of hanging.
- Add Spring Cloud Config Server for centralized, refreshable configuration instead of each service's own `application.yml`.
