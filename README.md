# User Microservice

Authentication, user profile management, two-factor authentication (TOTP), and watchlist management for the Neo4flix platform.

## Requirements

| Tool | Version |
|------|---------|
| Java | 25 |
| Maven | 3.9+ |
| PostgreSQL | 15+ |

## Cloning

```bash
git clone https://github.com/johneliud/user-microservice.git
cd user-microservice
```

## Configuration

The service uses two properties files:

**`src/main/resources/application.properties`** — contains environment variable placeholders (committed).

**`src/main/resources/application-secrets.properties`** — contains actual values for local development (gitignored). Create it manually:

```properties
server.port=8082

spring.datasource.url=jdbc:postgresql://localhost:5432/neo4flix
spring.datasource.username=postgres
spring.datasource.password=your_password

jwt.secret=your_32_plus_char_secret_here
jwt.expiration=86400000
jwt.refresh-expiration=604800000
jwt.mfa-expiration=300000

totp.issuer=Neo4flix
```

### Database Setup

```sql
-- Run as the postgres superuser
CREATE DATABASE neo4flix;
```

Flyway runs migrations automatically on startup — no manual schema setup needed.

## Running

```bash
./mvnw spring-boot:run
```

The service starts on `http://localhost:8082`. Flyway applies any pending migrations on first run.

## Testing

```bash
# All unit tests (no database required)
./mvnw test
```

## Docs

See [`docs/`](docs/) for:

- [Architecture](docs/architecture.md) — system position, package structure, security model, data flows, DB schema
- [API Reference](docs/api-reference.md) — all endpoints, request/response schemas
- [API Testing Guide](docs/api-testing.md) — Postman and curl examples for every endpoint