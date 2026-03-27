# API Reference — User Microservice

Base URL: `http://localhost:8082`

All authenticated endpoints require the header:
```
Authorization: Bearer <access_token>
```

---

## Authentication — `/api/auth`

### POST /api/auth/register

Register a new user account.

**Public** · Returns `201 Created`

**Request body**
```json
{
  "username": "johndoe",
  "email": "john@example.com",
  "password": "Secret@123"
}
```

Password rules: minimum 8 characters, at least one uppercase letter, one digit, and one special character (`@$!%*?&`).

**Response**
```json
{
  "accessToken": "eyJ...",
  "refreshToken": "eyJ...",
  "tokenType": "Bearer",
  "expiresIn": 86400000
}
```

**Errors**

| Status | Reason |
|--------|--------|
| 400 | Validation failure (weak password, invalid email, blank username) |
| 400 | Username or email already taken |

---

### POST /api/auth/login

Authenticate an existing user.

**Public** · Returns `200 OK`

**Request body**
```json
{
  "username": "johndoe",
  "password": "Secret@123"
}
```

**Response — 2FA disabled**
```json
{
  "accessToken": "eyJ...",
  "refreshToken": "eyJ...",
  "tokenType": "Bearer",
  "expiresIn": 86400000
}
```

**Response — 2FA enabled**
```json
{
  "requires2fa": true,
  "mfaToken": "eyJ..."
}
```

When `requires2fa` is `true`, use the `mfaToken` with `POST /api/auth/2fa/authenticate` to complete login.

**Errors**

| Status | Reason |
|--------|--------|
| 401 | Invalid username or password |

---

### POST /api/auth/refresh

Rotate the refresh token and obtain a new access token.

**Public** · Returns `200 OK`

Old refresh token is revoked on use (rotation).

**Request body**
```json
{
  "refreshToken": "eyJ..."
}
```

**Response** — same shape as register response.

**Errors**

| Status | Reason |
|--------|--------|
| 400 | Token not found, revoked, or expired |

---

### POST /api/auth/logout

Revoke a refresh token.

**Public** · Returns `204 No Content`

**Request body**
```json
{
  "refreshToken": "eyJ..."
}
```

Unknown tokens are silently ignored (idempotent).

---

## Two-Factor Authentication — `/api/auth/2fa`

### POST /api/auth/2fa/setup

Generate a TOTP secret and QR code URI for the authenticator app.

**Authenticated** · Returns `200 OK`

The `twoFactorEnabled` flag is NOT set yet — call `/verify` to confirm and enable.

**Response**
```json
{
  "secret": "BASE32SECRETKEY",
  "qrCodeUri": "otpauth://totp/Neo4flix:john@example.com?secret=BASE32SECRETKEY&issuer=Neo4flix"
}
```

---

### POST /api/auth/2fa/verify

Verify a TOTP code and enable 2FA on the account.

**Authenticated** · Returns `204 No Content`

**Request body**
```json
{
  "totpCode": "123456"
}
```

**Errors**

| Status | Reason |
|--------|--------|
| 400 | Invalid 6-digit code format |
| 400 | Incorrect TOTP code |
| 422 | Setup not started (no secret stored) |

---

### POST /api/auth/2fa/disable

Disable 2FA. Requires current password confirmation.

**Authenticated** · Returns `204 No Content`

**Request body**
```json
{
  "password": "Secret@123"
}
```

**Errors**

| Status | Reason |
|--------|--------|
| 400 | Incorrect password |

---

### POST /api/auth/2fa/authenticate

Complete login when 2FA is required. Uses the short-lived MFA token returned by `/login`.

**Public** · Returns `200 OK`

**Request body**
```json
{
  "mfaToken": "eyJ...",
  "totpCode": "123456"
}
```

**Response** — same shape as register response (full access + refresh tokens).

**Errors**

| Status | Reason |
|--------|--------|
| 400 | Invalid or expired MFA token |
| 400 | Incorrect TOTP code |
| 422 | 2FA not enabled on the account |

---

## User Profile — `/api/users`

### GET /api/users/profile

Retrieve the authenticated user's profile.

**Authenticated** · Returns `200 OK`

**Response**
```json
{
  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "username": "johndoe",
  "email": "john@example.com",
  "twoFactorEnabled": false,
  "createdAt": "2025-01-01T12:00:00"
}
```

---

### PUT /api/users/profile

Update username and/or email. Omit a field (or pass `null`) to leave it unchanged.

**Authenticated** · Returns `200 OK`

**Request body**
```json
{
  "username": "newname",
  "email": "new@example.com"
}
```

**Errors**

| Status | Reason |
|--------|--------|
| 400 | Username or email already taken |

---

### DELETE /api/users/profile

Permanently delete the authenticated user's account.

**Authenticated** · Returns `204 No Content`

---

## Admin — `/api/users`

These endpoints require `ROLE_ADMIN`.

### GET /api/users

List all registered users.

**Admin** · Returns `200 OK`

**Response**
```json
[
  {
    "id": "...",
    "username": "johndoe",
    "email": "john@example.com",
    "twoFactorEnabled": false,
    "createdAt": "2025-01-01T12:00:00"
  }
]
```

---

### DELETE /api/users/{id}

Delete a user by ID.

**Admin** · Returns `204 No Content`

**Errors**

| Status | Reason |
|--------|--------|
| 400 | User not found |

---

## Watchlist — `/api/users/profile/watchlist`

### GET /api/users/profile/watchlist

List all movies in the authenticated user's watchlist.

**Authenticated** · Returns `200 OK`

**Response**
```json
[
  {
    "movieId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "addedAt": "2025-01-01T12:00:00"
  }
]
```

---

### POST /api/users/profile/watchlist

Add a movie to the watchlist.

**Authenticated** · Returns `201 Created`

**Request body**
```json
{
  "movieId": "3fa85f64-5717-4562-b3fc-2c963f66afa6"
}
```

**Response** — the created watchlist item.

**Errors**

| Status | Reason |
|--------|--------|
| 400 | `movieId` is null |
| 400 | Movie already in watchlist |

---

### DELETE /api/users/profile/watchlist/{movieId}

Remove a movie from the watchlist.

**Authenticated** · Returns `204 No Content`

**Errors**

| Status | Reason |
|--------|--------|
| 400 | Movie not in watchlist |

---

## Data Model

### users

| Column | Type | Notes |
|--------|------|-------|
| id | UUID | PK, auto-generated |
| username | VARCHAR(50) | unique |
| email | VARCHAR(255) | unique |
| password_hash | VARCHAR(255) | BCrypt |
| enabled | BOOLEAN | default true |
| two_factor_enabled | BOOLEAN | default false |
| totp_secret | VARCHAR(255) | nullable, set during 2FA setup |
| created_at | TIMESTAMP | auto-set |

### user_roles

| Column | Type | Notes |
|--------|------|-------|
| user_id | UUID | FK → users |
| role | VARCHAR(50) | `ROLE_USER` or `ROLE_ADMIN` |

### refresh_tokens

| Column | Type | Notes |
|--------|------|-------|
| id | UUID | PK |
| token | VARCHAR(255) | unique |
| user_id | UUID | FK → users |
| expires_at | TIMESTAMP | |
| revoked | BOOLEAN | default false |

### watchlist

| Column | Type | Notes |
|--------|------|-------|
| id | UUID | PK |
| user_id | UUID | FK → users |
| movie_id | UUID | references movie-service (not enforced locally) |
| added_at | TIMESTAMP | auto-set |