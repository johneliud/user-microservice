# API Testing Guide — User Microservice

This guide covers testing every endpoint using **Postman** and **curl**.

Base URL: `http://localhost:8082`

---

## Postman Setup

### Environment variables

Create a Postman environment called **Neo4flix - Local** with these variables:

| Variable | Initial Value | Description |
|----------|--------------|-------------|
| `base_url` | `http://localhost:8082` | Service base URL |
| `access_token` | *(empty)* | Set automatically by login/register scripts |
| `refresh_token` | *(empty)* | Set automatically |
| `mfa_token` | *(empty)* | Set automatically when 2FA is required |
| `user_id` | *(empty)* | Set after login/register |

### Auto-capture tokens

Add this **Tests** script to the register and login requests to capture tokens automatically:

```javascript
const body = pm.response.json();

if (body.accessToken) {
    pm.environment.set("access_token", body.accessToken);
    pm.environment.set("refresh_token", body.refreshToken);
}

if (body.mfaToken) {
    pm.environment.set("mfa_token", body.mfaToken);
}
```

### Authorization

For authenticated endpoints, set the **Authorization** tab:
- Type: `Bearer Token`
- Token: `{{access_token}}`

---

## Authentication

### Register

**POST** `{{base_url}}/api/auth/register`

Headers: `Content-Type: application/json`

Body:
```json
{
  "username": "johndoe",
  "email": "john@example.com",
  "password": "Secret@123"
}
```

Expected: `201 Created` with `accessToken` and `refreshToken`.

curl:
```bash
curl -s -X POST http://localhost:8082/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"johndoe","email":"john@example.com","password":"Secret@123"}' | jq .
```

---

### Login (2FA disabled)

**POST** `{{base_url}}/api/auth/login`

Body:
```json
{
  "username": "johndoe",
  "password": "Secret@123"
}
```

Expected: `200 OK` with `accessToken` and `refreshToken`.

curl:
```bash
curl -s -X POST http://localhost:8082/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"johndoe","password":"Secret@123"}' | jq .
```

---

### Login (2FA enabled)

Same request as above. When 2FA is on, the response changes:

```json
{
  "requires2fa": true,
  "mfaToken": "eyJ..."
}
```

Use the `mfaToken` value in the `/api/auth/2fa/authenticate` request.

---

### Refresh Token

**POST** `{{base_url}}/api/auth/refresh`

Body:
```json
{
  "refreshToken": "{{refresh_token}}"
}
```

Expected: `200 OK` with new `accessToken` and `refreshToken`. The old refresh token is invalidated.

curl:
```bash
curl -s -X POST http://localhost:8082/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<your_refresh_token>"}' | jq .
```

---

### Logout

**POST** `{{base_url}}/api/auth/logout`

Body:
```json
{
  "refreshToken": "{{refresh_token}}"
}
```

Expected: `204 No Content`.

curl:
```bash
curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8082/api/auth/logout \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<your_refresh_token>"}'
```

---

## Two-Factor Authentication

### Full 2FA setup flow

**Step 1 — Generate secret**

**POST** `{{base_url}}/api/auth/2fa/setup`

Headers: `Authorization: Bearer {{access_token}}`

Expected: `200 OK`
```json
{
  "secret": "BASE32SECRETKEY",
  "qrCodeUri": "otpauth://totp/Neo4flix:john@example.com?secret=..."
}
```

Open the `qrCodeUri` in a browser or QR code renderer, then scan it with Google Authenticator, Authy, or any TOTP app.

curl:
```bash
curl -s -X POST http://localhost:8082/api/auth/2fa/setup \
  -H "Authorization: Bearer $ACCESS_TOKEN" | jq .
```

---

**Step 2 — Verify and enable**

**POST** `{{base_url}}/api/auth/2fa/verify`

Headers: `Authorization: Bearer {{access_token}}`

Body (use the 6-digit code from your authenticator app):
```json
{
  "totpCode": "123456"
}
```

Expected: `204 No Content`. 2FA is now active on the account.

curl:
```bash
curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8082/api/auth/2fa/verify \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"totpCode":"123456"}'
```

---

**Step 3 — Login with 2FA**

1. Call `POST /api/auth/login` — receive `mfaToken`
2. Get the current 6-digit code from your authenticator app
3. Call authenticate:

**POST** `{{base_url}}/api/auth/2fa/authenticate`

Body:
```json
{
  "mfaToken": "{{mfa_token}}",
  "totpCode": "123456"
}
```

Expected: `200 OK` with full `accessToken` and `refreshToken`.

curl:
```bash
curl -s -X POST http://localhost:8082/api/auth/2fa/authenticate \
  -H "Content-Type: application/json" \
  -d '{"mfaToken":"<mfa_token>","totpCode":"123456"}' | jq .
```

---

**Disable 2FA**

**POST** `{{base_url}}/api/auth/2fa/disable`

Headers: `Authorization: Bearer {{access_token}}`

Body:
```json
{
  "password": "Secret@123"
}
```

Expected: `204 No Content`.

curl:
```bash
curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8082/api/auth/2fa/disable \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"password":"Secret@123"}'
```

---

## User Profile

### Get profile

**GET** `{{base_url}}/api/users/profile`

Headers: `Authorization: Bearer {{access_token}}`

Expected: `200 OK`
```json
{
  "id": "...",
  "username": "johndoe",
  "email": "john@example.com",
  "twoFactorEnabled": false,
  "createdAt": "2025-01-01T12:00:00"
}
```

curl:
```bash
curl -s http://localhost:8082/api/users/profile \
  -H "Authorization: Bearer $ACCESS_TOKEN" | jq .
```

---

### Update profile

**PUT** `{{base_url}}/api/users/profile`

Headers: `Authorization: Bearer {{access_token}}`

Body (omit or null any field to leave unchanged):
```json
{
  "username": "newusername",
  "email": null
}
```

Expected: `200 OK` with updated profile.

curl:
```bash
curl -s -X PUT http://localhost:8082/api/users/profile \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"username":"newusername","email":null}' | jq .
```

---

### Delete account

**DELETE** `{{base_url}}/api/users/profile`

Headers: `Authorization: Bearer {{access_token}}`

Expected: `204 No Content`.

curl:
```bash
curl -s -o /dev/null -w "%{http_code}" -X DELETE http://localhost:8082/api/users/profile \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

---

## Admin Endpoints

These require an account with `ROLE_ADMIN`. Assign the role directly in the database:

```sql
INSERT INTO user_roles (user_id, role)
VALUES ('<your_user_id>', 'ROLE_ADMIN');
```

Then log in again to get a token carrying the admin role.

### List all users

**GET** `{{base_url}}/api/users`

Headers: `Authorization: Bearer {{access_token}}`

Expected: `200 OK` with array of user profiles.

curl:
```bash
curl -s http://localhost:8082/api/users \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq .
```

---

### Delete a user

**DELETE** `{{base_url}}/api/users/{id}`

Headers: `Authorization: Bearer {{access_token}}`

Replace `{id}` with the target user's UUID.

curl:
```bash
curl -s -o /dev/null -w "%{http_code}" \
  -X DELETE http://localhost:8082/api/users/3fa85f64-5717-4562-b3fc-2c963f66afa6 \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

---

## Watchlist

### Get watchlist

**GET** `{{base_url}}/api/users/profile/watchlist`

Headers: `Authorization: Bearer {{access_token}}`

Expected: `200 OK` with array (empty array if nothing added).

curl:
```bash
curl -s http://localhost:8082/api/users/profile/watchlist \
  -H "Authorization: Bearer $ACCESS_TOKEN" | jq .
```

---

### Add movie to watchlist

**POST** `{{base_url}}/api/users/profile/watchlist`

Headers: `Authorization: Bearer {{access_token}}`

Body:
```json
{
  "movieId": "3fa85f64-5717-4562-b3fc-2c963f66afa6"
}
```

Expected: `201 Created`
```json
{
  "movieId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "addedAt": "2025-01-01T12:00:00"
}
```

curl:
```bash
curl -s -X POST http://localhost:8082/api/users/profile/watchlist \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"movieId":"3fa85f64-5717-4562-b3fc-2c963f66afa6"}' | jq .
```

---

### Remove movie from watchlist

**DELETE** `{{base_url}}/api/users/profile/watchlist/{movieId}`

Headers: `Authorization: Bearer {{access_token}}`

Expected: `204 No Content`.

curl:
```bash
curl -s -o /dev/null -w "%{http_code}" \
  -X DELETE http://localhost:8082/api/users/profile/watchlist/3fa85f64-5717-4562-b3fc-2c963f66afa6 \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

---

## Common Error Responses

All errors follow [RFC 9457 Problem Details](https://www.rfc-editor.org/rfc/rfc9457):

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Username is already taken",
  "instance": "/api/auth/register"
}
```

| Status | Meaning |
|--------|---------|
| 400 | Validation failure or business rule violation |
| 401 | Missing/invalid credentials or expired token |
| 403 | Insufficient role (e.g. non-admin on admin endpoint) |
| 422 | State precondition not met (e.g. 2FA setup not started) |

---

## Postman Collection (import-ready)

You can build the full collection by:

1. Creating a new collection named **Neo4flix - User Microservice**
2. Adding a folder per section (Auth, 2FA, Profile, Admin, Watchlist)
3. Setting collection-level variables to `{{base_url}}` and `{{access_token}}`
4. Adding the Tests script from the [Postman Setup](#postman-setup) section to the register and login requests

Alternatively, export the environment and collection JSON files and share them with your team.