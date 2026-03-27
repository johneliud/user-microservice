# Architecture — User Microservice

## Overview

The User Microservice is a stateless Spring Boot service responsible for:

- User registration and authentication (JWT-based)
- Refresh token rotation
- Two-factor authentication (TOTP)
- User profile management
- Per-user movie watchlist

It is one of five services that make up the Neo4flix backend, exposed to clients exclusively through the API Gateway.

---

## Technology Stack

| Layer | Technology |
|-------|-----------|
| Runtime | Java 25 |
| Framework | Spring Boot 4.0.4 · Spring MVC (servlet stack) |
| Security | Spring Security 7 · JJWT 0.12.5 (HS256) |
| 2FA | `dev.samstevens.totp` 1.7.1 (RFC 6238 TOTP) |
| Persistence | Spring Data JPA · Hibernate · PostgreSQL 15 |
| Migrations | Flyway |
| Validation | Jakarta Bean Validation 3 |
| Build | Maven 3.9+ |

---

## Position in the System

```
                        ┌──────────────────────────────────────────────┐
                        │                 Neo4flix Backend             │
                        │                                              │
  Client (Angular) ───► │  API Gateway :8080                          │
                        │       │                                      │
                        │       ├──► User Microservice    :8082  ◄─── │
                        │       ├──► Movie Service        :8083        │
                        │       ├──► Rating Service       :8084        │
                        │       └──► Recommendation Svc   :8085        │
                        └──────────────────────────────────────────────┘
                                           │
                                    PostgreSQL :5432
                                    (neo4flix database)
```

The API Gateway:
- Validates JWT signatures on all protected routes before forwarding
- Rate-limits login and register endpoints
- Adds CORS and security headers
- Routes `/api/auth/**` and `/api/users/**` to this service

This service does **not** communicate with other microservices directly. The watchlist stores only `movieId` (UUID), relying on the movie-service (not yet implemented) for movie data.

---

## Package Structure

```
io.github.johneliud.user_microservice/
│
├── config/
│   └── SecurityConfig.java          # Filter chain, method security, BCrypt bean
│
├── controller/
│   ├── AuthController.java          # POST /api/auth/**
│   ├── TwoFactorAuthController.java # POST /api/auth/2fa/**
│   ├── UserController.java          # GET|PUT|DELETE /api/users/**
│   └── WatchlistController.java     # GET|POST|DELETE /api/users/profile/watchlist/**
│
├── dto/                             # Request/response records (immutable)
│   ├── RegisterRequest.java
│   ├── LoginRequest.java
│   ├── LoginResponse.java           # Sealed interface
│   ├── AuthResponse.java            # implements LoginResponse
│   ├── MfaRequiredResponse.java     # implements LoginResponse
│   ├── RefreshTokenRequest.java
│   ├── TwoFactorAuthRequest.java
│   ├── TwoFactorSetupResponse.java
│   ├── TwoFactorVerifyRequest.java
│   ├── TwoFactorDisableRequest.java
│   ├── UpdateProfileRequest.java
│   ├── UserProfileResponse.java
│   ├── WatchlistRequest.java
│   └── WatchlistItemResponse.java
│
├── entity/
│   ├── Role.java                    # Enum: ROLE_USER, ROLE_ADMIN
│   ├── User.java                    # JPA entity
│   ├── RefreshToken.java            # JPA entity
│   └── Watchlist.java               # JPA entity
│
├── exception/
│   └── GlobalExceptionHandler.java  # @RestControllerAdvice → RFC 9457 ProblemDetail
│
├── filter/
│   └── JwtAuthenticationFilter.java # OncePerRequestFilter — validates Bearer tokens
│
├── repository/
│   ├── UserRepository.java
│   ├── RefreshTokenRepository.java
│   └── WatchlistRepository.java
│
├── service/
│   ├── AuthService.java
│   ├── TwoFactorAuthService.java
│   ├── UserDetailsServiceImpl.java  # Spring Security UserDetailsService
│   ├── UserService.java
│   └── WatchlistService.java
│
└── util/
    ├── JwtUtil.java                 # Token generation and validation
    └── TotpUtil.java                # Secret generation, QR URI, code verification
```

---

## Layered Architecture

```
  HTTP Request
       │
       ▼
  ┌─────────────────────────────────┐
  │  Spring Security Filter Chain   │
  │  JwtAuthenticationFilter        │  Validates Bearer token, populates SecurityContext
  └─────────────────────────────────┘
       │
       ▼
  ┌─────────────────────────────────┐
  │  Controller Layer               │  Maps HTTP ↔ DTO, extracts Authentication principal
  └─────────────────────────────────┘
       │
       ▼
  ┌─────────────────────────────────┐
  │  Service Layer                  │  Business logic, validation, token management
  └─────────────────────────────────┘
       │
       ▼
  ┌─────────────────────────────────┐
  │  Repository Layer               │  Spring Data JPA interfaces
  └─────────────────────────────────┘
       │
       ▼
  ┌─────────────────────────────────┐
  │  PostgreSQL                     │  Managed via Flyway migrations
  └─────────────────────────────────┘
```

Controllers are thin — they extract the principal (`UUID userId`) from the `Authentication` object and delegate immediately to the service. No business logic lives in controllers.

---

## Security Architecture

### JWT Structure

Two token types are issued, both signed with HS256:

**Access Token** (short-lived, default 24h)
```
{
  "sub": "<user UUID>",
  "role": "ROLE_USER",
  "iat": <unix timestamp>,
  "exp": <unix timestamp>
}
```

**MFA Token** (short-lived, default 5 min)
```
{
  "sub": "<user UUID>",
  "scope": "mfa",
  "iat": <unix timestamp>,
  "exp": <unix timestamp>
}
```

The `scope: mfa` claim prevents MFA tokens from being used as access tokens. `JwtUtil.validateMfaToken()` explicitly rejects tokens that lack this claim.

### Filter Chain

```
Incoming request
       │
       ├── /api/auth/register
       ├── /api/auth/login        ──► permitAll (no filter processing)
       ├── /api/auth/refresh
       ├── /api/auth/logout
       └── /api/auth/2fa/authenticate
       │
       └── all other paths ──► JwtAuthenticationFilter
                                    │
                          extract Authorization: Bearer <token>
                                    │
                          JwtUtil.validateToken(token)
                                    │
                          ┌─────────┴─────────┐
                        valid               invalid
                          │                   │
                  set SecurityContext    clear SecurityContext
                  (userId, role)        (Spring Security returns 401)
                          │
                  continue filter chain
                          │
                  @PreAuthorize check (admin endpoints)
                          │
                  controller method
```

The filter never throws — on any validation failure it clears the context and lets Spring Security's authorization layer return the 401/403 response.

### Role-Based Access

| Endpoint pattern | Required role |
|-----------------|--------------|
| `/api/auth/**` | Public (no token) |
| `/api/auth/2fa/authenticate` | Public (MFA token in body, not header) |
| `/api/users/profile/**` | Any authenticated user |
| `/api/users/profile/watchlist/**` | Any authenticated user |
| `GET /api/users` | `ROLE_ADMIN` |
| `DELETE /api/users/{id}` | `ROLE_ADMIN` |

Admin authorization is enforced by `@PreAuthorize("hasAuthority('ROLE_ADMIN')")` on the controller methods, backed by `@EnableMethodSecurity` in `SecurityConfig`.

---

## Authentication Flows

### Standard Login

```
Client                      AuthController         AuthService            DB
  │                               │                     │                  │
  │── POST /api/auth/login ──────►│                     │                  │
  │                               │── login(request) ──►│                  │
  │                               │                     │── findByUsername ►│
  │                               │                     │◄─────────────────│
  │                               │             AuthenticationManager      │
  │                               │                     │── authenticate   │
  │                               │                     │   (BCrypt check) │
  │                               │                     │                  │
  │                               │                     │── generateToken  │
  │                               │                     │── save refresh   ►│
  │◄── 200 AuthResponse ─────────│◄── AuthResponse ───│                  │
```

### Two-Factor Login

```
Client                  AuthController      TwoFactorAuthController     Services
  │                          │                       │                      │
  │── POST /login ──────────►│                       │                      │
  │◄── 200 MfaRequired ─────│  (mfaToken: short JWT │                      │
  │    {requires2fa, mfaToken}  with scope=mfa)      │                      │
  │                          │                       │                      │
  │  [user opens authenticator app, reads 6-digit code]                     │
  │                          │                       │                      │
  │── POST /2fa/authenticate ─────────────────────►│                      │
  │   {mfaToken, totpCode}   │                       │                      │
  │                          │               validateMfaToken               │
  │                          │               TotpUtil.verifyCode            │
  │                          │               generateToken (access)        │
  │                          │               save refresh token            │
  │◄── 200 AuthResponse ─────────────────────────────│◄────────────────────│
```

### Refresh Token Rotation

```
Client                       AuthService                   DB
  │                               │                         │
  │── POST /api/auth/refresh ────►│                         │
  │   {refreshToken}              │── findByToken ─────────►│
  │                               │◄────────────────────────│
  │                               │   check: not revoked    │
  │                               │   check: not expired    │
  │                               │── revoke old token ────►│
  │                               │── generateToken (new)   │
  │                               │── save new refresh ────►│
  │◄── 200 AuthResponse ─────────│                         │
```

---

## 2FA Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                     TOTP Setup Flow                          │
│                                                              │
│  TotpUtil.generateSecret()                                   │
│      └── dev.samstevens.totp SecretGenerator                 │
│              └── 160-bit Base32 random secret                │
│                                                              │
│  TotpUtil.generateQrCodeUri(secret, email)                   │
│      └── "otpauth://totp/{issuer}:{email}?secret=...         │
│               &issuer={issuer}"                              │
│                                                              │
│  User scans QR → authenticator app registers the secret      │
│  User enters 6-digit code → TotpUtil.verifyCode()            │
│      └── validates against ±1 time window (30-second steps)  │
│  On success: user.twoFactorEnabled = true                    │
└──────────────────────────────────────────────────────────────┘
```

The TOTP secret is stored in the `totp_secret` column (plain Base32). It is never returned to the client after the setup response.

---

## Database Schema

```
┌──────────────────────────────┐
│           users              │
├──────────────────────────────┤
│ id            UUID  PK       │
│ username      VARCHAR(50)    │◄──────────────┐
│ email         VARCHAR(255)   │               │
│ password_hash VARCHAR(255)   │               │
│ enabled       BOOLEAN        │               │
│ two_factor_enabled BOOLEAN   │               │
│ totp_secret   VARCHAR(255)   │               │
│ created_at    TIMESTAMP      │               │
└──────────────────────────────┘               │
         │ 1                                   │
         │                                     │
         │ N                           UserDetailsServiceImpl
         ▼                             loads user by username
┌──────────────────────────────┐
│          user_roles          │
├──────────────────────────────┤
│ user_id  UUID  FK → users    │
│ role     VARCHAR(50)         │
│ PK (user_id, role)           │
└──────────────────────────────┘

         │ 1
         ├──────────────────────────────────────────────┐
         │ N                                            │ N
         ▼                                              ▼
┌──────────────────────────────┐       ┌──────────────────────────────┐
│        refresh_tokens        │       │          watchlist           │
├──────────────────────────────┤       ├──────────────────────────────┤
│ id         UUID  PK          │       │ id       UUID  PK            │
│ token      VARCHAR(255) UNIQ │       │ user_id  UUID  FK → users    │
│ user_id    UUID  FK → users  │       │ movie_id UUID                │
│ expires_at TIMESTAMP         │       │ added_at TIMESTAMP           │
│ revoked    BOOLEAN           │       │ UNIQUE (user_id, movie_id)   │
└──────────────────────────────┘       └──────────────────────────────┘
                                                    │
                                       movie_id references
                                       movie-service (not enforced
                                       at DB level — cross-service)
```

### Migrations

Managed by Flyway in `src/main/resources/db/migration/`:

| File | Contents |
|------|---------|
| `V1__create_users_table.sql` | `users` + `user_roles` tables |
| `V2__create_refresh_tokens_table.sql` | `refresh_tokens` + index on `user_id` |
| `V3__create_watchlist_table.sql` | `watchlist` + unique constraint + index |

---

## DTO Design

All DTOs are Java `record` types — immutable, with no-arg construction disabled by design. Validation annotations live directly on the record components.

**`LoginResponse`** is a sealed interface:

```
LoginResponse (sealed)
├── AuthResponse       — full tokens, returned when 2FA is off or after MFA step
└── MfaRequiredResponse — mfaToken only, returned when 2FA is required
```

This allows `AuthController.login()` to return a polymorphic response without casting at the call site, and Jackson serializes the concrete type transparently.

---

## Error Handling

`GlobalExceptionHandler` (`@RestControllerAdvice`) maps exceptions to RFC 9457 `ProblemDetail` responses:

| Exception | HTTP Status |
|-----------|------------|
| `MethodArgumentNotValidException` | 400 — field-level validation errors |
| `IllegalArgumentException` | 400 — business rule violations (duplicate user, wrong password, etc.) |
| `BadCredentialsException` | 401 — wrong username/password |
| `IllegalStateException` | 422 — precondition failures (2FA not set up) |

Spring Security's access denied and authentication failures return 401/403 directly from the security filter chain, before reaching the controller advice.

---

## Configuration

All sensitive values are externalized via environment variables in production and via `application-secrets.properties` locally. No credentials are committed to source control.

| Property | Env variable | Description |
|----------|-------------|-------------|
| `jwt.secret` | `JWT_SECRET` | HS256 signing key (min 32 chars) |
| `jwt.expiration` | `JWT_EXPIRATION` | Access token TTL in ms (default 86400000 = 24h) |
| `jwt.refresh-expiration` | `JWT_REFRESH_EXPIRATION` | Refresh token TTL in ms (default 604800000 = 7d) |
| `jwt.mfa-expiration` | `JWT_MFA_EXPIRATION` | MFA token TTL in ms (default 300000 = 5min) |
| `totp.issuer` | `TOTP_ISSUER` | Issuer name shown in authenticator apps |
| `spring.datasource.*` | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL connection |
| `server.port` | `SERVER_PORT` | Default 8082 |