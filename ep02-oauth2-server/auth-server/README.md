# Authorization Server

REST authentication and token service for the Consumer and Management applications. Built with Java 25, Spring Boot, Spring Security Authorization Server, JPA, and PostgreSQL.

Clients use their own login screen, submit credentials to `/api/auth`, and exchange the returned login ticket for tokens. This is a custom login-ticket grant; the ticket is not an OpenID Connect ID token. No hosted login form or redirect flow is enabled.

## Run locally

Requirements: JDK 25, Maven, and Docker Compose. Run these commands from the project root (`ep02-oauth2-server`):

```bash
# Set the PostgreSQL container's superuser password for this shell.
export POSTGRES_PASSWORD=postgres

docker compose up -d
mvn -pl auth-server spring-boot:run
```

Each service has a separate default HTTP port:

| Service | Default port | Base URL |
| --- | --- | --- |
| auth-server | 8080 | `http://localhost:8080` |
| consumer-service | 8081 | `http://localhost:8081` |
| management-service | 8082 | `http://localhost:8082` |

Each service supports a `SERVER_PORT` override. Set it separately for each process; a shared exported value would assign the same port to every service. If you change the auth-server port, also update `AUTH_ISSUER` in all services and `AUTH_INTROSPECTION_URI` in both resource services.

The authorization server listens at `http://localhost:8080`. Open [Mailpit](http://localhost:8025) to read development OTP emails. PostgreSQL uses port `5432`, SMTP uses `1025`, and Kafka uses `9092`.

PostgreSQL initialization executes these scripts on the first startup of an empty database volume:

- [`01-init.sql`](../postgres/01-init.sql): creates service roles and schemas in `wallet_demo`.
- [`02-auth-schema.sql`](../postgres/02-auth-schema.sql): creates the auth tables and constraints.

Hibernate uses `ddl-auto: validate`; it does not create or update tables. During this planning phase, update the SQL scripts directly. To recreate the development database after schema changes:

```bash
# Deletes Compose volumes, including PostgreSQL data and Kafka data.
docker compose down -v
docker compose up -d
```

## Configuration

Defaults are in [`application.yml`](src/main/resources/application.yml) and target applications running on the host with infrastructure in Docker.

| Environment variable | Default | Purpose |
| --- | --- | --- |
| `SERVER_PORT` | `8080` | Authorization server HTTP port |
| `AUTH_DB_URL` | `jdbc:postgresql://localhost:5432/wallet_demo?currentSchema=auth` | JDBC connection |
| `AUTH_DB_USERNAME` | `authuser` | Database role |
| `AUTH_DB_PASSWORD` | `authpass` | Database password |
| `ACCESS_TOKEN_TIME_TO_LIVE` | `10m` | Access-token lifetime |
| `REFRESH_TOKEN_TIME_TO_LIVE` | `8h` | Refresh-token lifetime |
| `AUTH_ISSUER` | `http://localhost:8080` | Token issuer |
| `ADMINISTRATOR_USERNAME` | `admin@example.com` | Initial administrator email |
| `ADMINISTRATOR_PASSWORD` | `password` | Initial administrator password |
| `CONSUMER_CLIENT_SECRET` | `consumer-demo-secret` | Consumer OAuth client secret |
| `MANAGEMENT_CLIENT_SECRET` | `management-demo-secret` | Management OAuth client secret |
| `CONSUMER_RESOURCE_SECRET` | `consumer-resource-secret` | Consumer service introspection credential |
| `MANAGEMENT_RESOURCE_SECRET` | `management-resource-secret` | Management service introspection credential |
| `MAIL_HOST` / `MAIL_PORT` | `localhost` / `1025` | SMTP connection |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | Empty | SMTP credentials |
| `MAIL_SMTP_AUTH` | `false` | SMTP authentication |
| `MAIL_SMTP_STARTTLS_ENABLE` | `false` | SMTP STARTTLS |
| `MAIL_FROM` | `no-reply@wallet-demo.local` | Sender address |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker |
| `KAFKA_CONSUMER_GROUP_ID` | `auth-server` | Kafka consumer group |

`POSTGRES_PASSWORD` configures the container superuser; it does not change the `authuser` password defined in `01-init.sql`. Keep the SQL and application database credentials consistent when changing them.

Other application settings are configured directly under `app` in YAML:

| Property | Default |
| --- | --- |
| `app.auth.ticket-lifetime-seconds` | 60 seconds |
| `app.otp.expiration.registration` | 3 minutes |
| `app.otp.expiration.activation` | 5 minutes |
| `app.otp.resend.interval-seconds` | 60 seconds |
| `app.otp.cooldown.missing-count` | 3 failed attempts |
| `app.otp.cooldown.waiting-duration` | 30 minutes |

Token lifetimes apply to all registered clients. Configure positive durations using units such as `30s`, `15m`, or `12h`, or ISO-8601 values such as `PT15M`:

```yaml
app:
  auth:
    access-token-time-to-live: ${ACCESS_TOKEN_TIME_TO_LIVE:10m}
    refresh-token-time-to-live: ${REFRESH_TOKEN_TIME_TO_LIVE:8h}
```

Restart the server after changing these settings. Token response examples below assume the default lifetimes.

Mail uses MIME messages with HTML content. Development credentials are examples; replace them for deployed environments. Client secrets belong in a trusted backend and must not be embedded in browser or mobile application code.

## Identity, profiles, and administrator

An identity stores the username. A profile stores the password, system, role, status, and token version. An identity can have one profile per system, allowing separate passwords for Consumer and Management.

| Profile | System | Role | Initial status |
| --- | --- | --- | --- |
| Consumer registration | Consumer | Consumer | Pending |
| Management activation | Management | Management | Active |
| Bootstrap administrator | Management | Administrator | Active |

At startup, `AdministratorInitializer` creates an administrator only when there are no profiles. The administrator does not require OTP activation. Changing administrator properties later does not reset an existing account.

## Authenticate and obtain tokens

The bootstrap administrator provides a working local example. Response samples use illustrative UUIDs, timestamps, and token placeholders; actual values are generated at runtime.

### 1. Authenticate

Request body: `application/json`. All fields are required.

| Parameter Name | Sample Value | Definition |
| --- | --- | --- |
| `clientId` | `management-demo` | Registered client ID that determines which profile system is authenticated. |
| `username` | `admin@example.com` | Identity username; currently an email address. |
| `password` | `password` | Password for the profile belonging to the client's system. |

```bash
curl -X POST http://localhost:8080/api/auth \
  -H 'Content-Type: application/json' \
  -d '{"clientId":"management-demo","username":"admin@example.com","password":"password"}'
```

Response: `200 OK`, `application/json`.

```json
{
  "loginTicket": "<one-time-ticket>",
  "expiresIn": 60
}
```

Authentication requires an Active profile with a compatible system and role. The ticket is bound to the client, expires quickly, and can be consumed once.

### 2. Exchange the ticket

Request body: `application/x-www-form-urlencoded`. Both form fields are required. Authenticate with HTTP Basic using the client ID and secret.

| Parameter Name | Sample Value | Definition |
| --- | --- | --- |
| `grant_type` | `urn:jdc:params:oauth:grant-type:login-ticket` | Form field selecting the custom login-ticket grant. |
| `login_ticket` | `<one-time-ticket>` | Form field containing the unexpired, unused ticket returned by `/api/auth` for this client. |
| HTTP Basic username | `management-demo` | Registered OAuth client ID, supplied through HTTP Basic authentication. |
| HTTP Basic password | `management-demo-secret` | Secret configured for that OAuth client. |

```bash
curl -X POST http://localhost:8080/oauth2/token \
  -u 'management-demo:management-demo-secret' \
  --data-urlencode 'grant_type=urn:jdc:params:oauth:grant-type:login-ticket' \
  --data-urlencode 'login_ticket=<one-time-ticket>'
```

Response: `200 OK`, `application/json`.

```json
{
  "access_token": "<access-token-jwt>",
  "token_type": "Bearer",
  "expires_in": 599,
  "refresh_token": "<refresh-token>",
  "scope": "management.api"
}
```

`expires_in` is the remaining access-token lifetime in seconds and may be slightly below 600.

The response contains `access_token`, `token_type`, `expires_in`, `refresh_token`, and `scope`. Access tokens are JWTs with a 10-minute lifetime. Refresh tokens are opaque credentials with an 8-hour lifetime and rotate on refresh.

| Client ID | Profile system | Scope and audience |
| --- | --- | --- |
| `consumer-demo` | Consumer | `consumer.api` |
| `management-demo` | Management | `management.api` |

Access token claims include `sub` (profile ID), `system`, `roles`, `token_version`, issuer, audience, and expiration. An administrator receives `system: Management` and `roles: ["Administrator"]`.

### 3. Refresh tokens

Request body: `application/x-www-form-urlencoded`. Both form fields and HTTP Basic client authentication are required.

| Parameter Name | Sample Value | Definition |
| --- | --- | --- |
| `grant_type` | `refresh_token` | Form field selecting the refresh-token grant. |
| `refresh_token` | `<refresh-token>` | Latest refresh token issued to this client and profile. |
| HTTP Basic username | `management-demo` | OAuth client ID that originally obtained the refresh token. |
| HTTP Basic password | `management-demo-secret` | Secret configured for that OAuth client. |

```bash
curl -X POST http://localhost:8080/oauth2/token \
  -u 'management-demo:management-demo-secret' \
  --data-urlencode 'grant_type=refresh_token' \
  --data-urlencode 'refresh_token=<refresh-token>'
```

Response: `200 OK`, `application/json`.

```json
{
  "access_token": "<new-access-token-jwt>",
  "token_type": "Bearer",
  "expires_in": 599,
  "refresh_token": "<new-refresh-token>",
  "scope": "management.api"
}
```

Store the newly returned refresh token for the next refresh request.

### 4. Log out all devices for the profile

No request body or path parameters are required.

| Parameter Name | Sample Value | Definition |
| --- | --- | --- |
| `Authorization` | `Bearer <access-token>` | Required HTTP header containing a valid access token for the profile to log out. |

```bash
curl -X POST http://localhost:8080/api/logout \
  -H 'Authorization: Bearer <access-token>'
```

Response: `200 OK` with an empty body. No JSON document is returned.

Successful logout returns `200 OK` with no body. It increments the authenticated profile's token version, invalidating its existing access tokens, refresh tokens, and outstanding login tickets. Another profile belonging to the same identity is unaffected.

## Consumer registration

All request bodies below are JSON. Successful calls currently return `200 OK`; verification and resend methods return no body.

| Method | Endpoint | Request body | Response |
| --- | --- | --- | --- |
| POST | `/api/registration` | `{"email":"consumer@example.com"}` | `{"key":"<registration-id>"}` |
| POST | `/api/registration/{id}/otp/validate` | `{"otpCode":"123456"}` | Empty |
| POST | `/api/registration/{id}/otp/resend` | None | Empty |
| POST | `/api/registration/{id}/initialize` | `{"password":"your-password"}` | `{"id":"<profile-id>"}` |

Request parameters (all required for the applicable endpoint):

| Parameter Name | Sample Value | Definition |
| --- | --- | --- |
| `email` | `consumer@example.com` | JSON field for `POST /api/registration`; a valid, nonblank email address that receives the verification code. |
| `id` | `550e8400-e29b-41d4-a716-446655440000` | Path parameter for verification, resend, and initialization; use the registration UUID returned as `key`. |
| `otpCode` | `123456` | JSON string for OTP validation; exactly six numeric digits. Use a string to preserve leading zeros. |
| `password` | `your-password` | Nonblank JSON field for initialization; becomes the Consumer profile password after successful verification. |

The resend endpoint requires only the registration ID in the path and has no request body.

### Response samples

`POST /api/registration` — `200 OK`, `application/json`:

```json
{
  "key": "550e8400-e29b-41d4-a716-446655440000"
}
```

`POST /api/registration/{id}/initialize` — `200 OK`, `application/json`:

```json
{
  "id": "6f3e8e9a-94c7-4aa6-b061-293f8e09a101"
}
```

The `key` identifies the registration record; the initialization response's `id` identifies the created profile.

`POST /api/registration/{id}/otp/validate` and `POST /api/registration/{id}/otp/resend` return `200 OK` with an empty body on success. No JSON document is returned.

Register the email, read the six-digit code in Mailpit, verify it, and initialize the password. The registration response's `key` is the UUID used as `{id}` in subsequent requests. Initialization creates a Pending Consumer profile, which cannot authenticate until activated. Automatic approval through Kafka is not implemented yet.

## Management activation

These endpoints require an existing activation record; `{id}` is its UUID.

| Method | Endpoint | Request body | Response |
| --- | --- | --- | --- |
| POST | `/api/activate/{id}` | None | Empty |
| POST | `/api/activate/{id}/otp/validate` | `{"otpCode":"123456"}` | Empty |
| POST | `/api/activate/{id}/otp/resend` | None | Empty |
| POST | `/api/activate/{id}/initialize` | `{"password":"your-password"}` | `{"id":"<profile-id>"}` |

Request parameters (all required for the applicable endpoint):

| Parameter Name | Sample Value | Definition |
| --- | --- | --- |
| `id` | `550e8400-e29b-41d4-a716-446655440000` | Path parameter for every activation endpoint; identifies an existing, unused activation record. |
| `otpCode` | `123456` | JSON string for OTP validation; exactly six numeric digits sent to the activation record's email address. |
| `password` | `your-password` | Nonblank JSON field for initialization; becomes the Management profile password after successful verification. |

The initial code request and resend endpoints require only the activation ID in the path and have no request body.

### Response samples

`POST /api/activate/{id}/initialize` — `200 OK`, `application/json`:

```json
{
  "id": "7b16b09f-a949-4b74-a7e3-87b9c7d32202"
}
```

The `id` identifies the initialized Management profile.

`POST /api/activate/{id}`, `POST /api/activate/{id}/otp/validate`, and `POST /api/activate/{id}/otp/resend` return `200 OK` with an empty body on success. No JSON document is returned.

Request a code, verify the email, and initialize the password. Initialization creates an Active Management profile and marks the activation record as used. Event-driven activation record creation and activation-link delivery are not implemented yet.

## Resource-server token validation

Resource servers call the custom internal endpoint with their own HTTP Basic credentials:

Request body: `application/x-www-form-urlencoded`. The token and HTTP Basic resource-server authentication are required.

| Parameter Name | Sample Value | Definition |
| --- | --- | --- |
| `token` | `<access-token>` | Form field containing the access token to validate. |
| HTTP Basic username | `management-service` | Resource-server ID configured under `app.auth.resource-secrets`. |
| HTTP Basic password | `management-resource-secret` | Secret for the resource server; separate from OAuth client secrets. |

```bash
curl -X POST http://localhost:8080/api/internal/tokens/introspect \
  -u 'management-service:management-resource-secret' \
  --data-urlencode 'token=<access-token>'
```

Valid-token response: `200 OK`, `application/json`. This sample shows selected attributes; additional JWT claims may also be present. Times are Unix timestamps in seconds.

```json
{
  "active": true,
  "sub": "7b16b09f-a949-4b74-a7e3-87b9c7d32202",
  "iss": "http://localhost:8080",
  "aud": ["management.api"],
  "iat": 1795170600,
  "exp": 1795171200,
  "scope": "management.api",
  "system": "Management",
  "roles": ["Administrator"],
  "token_version": 0
}
```

Invalid, expired, revoked, or disallowed-audience token response: `200 OK`, `application/json`:

```json
{
  "active": false
}
```

Missing or invalid resource-server credentials produce `401 Unauthorized` with a ProblemDetail response instead.

A valid token returns `active: true` and token attributes. Invalid tokens or disallowed audiences return `{"active":false}`. Resource credentials are separate from OAuth client credentials. Audience permissions are configured under `app.auth.resource-audiences`; the current Consumer service accepts both `consumer.api` and `management.api`, while Management service accepts `management.api`.

The included resource services validate tokens through this endpoint on every request, allowing logout and profile status changes to take effect without waiting for JWT expiration. Roles are mapped to authorities such as `ROLE_Administrator`.

## Security and error responses

Registration, activation, and authentication endpoints allow unauthenticated requests. Logout requires a bearer access token. Internal introspection requires resource-server credentials. `/oauth2/token` requires OAuth client authentication using HTTP Basic.

Other authorization-server endpoints are disabled, including the hosted login form, authorization redirect endpoint, standard introspection and revocation endpoints, discovery metadata, and public JWK endpoint. OpenID Connect ID tokens are not issued. Security is stateless and CSRF protection is disabled for these API flows.

Controller errors and delegated security errors are handled by `ExceptionHandlers` using `application/problem+json`, for example:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Please correct the invalid request fields.",
  "instance": "/api/auth",
  "errors": [
    {"field": "password", "message": "Enter password"}
  ]
}
```

The `errors` array is included for request field validation failures.

| Status | Meaning |
| --- | --- |
| 400 | Invalid request, OTP failure, or business rule violation |
| 401 | Authentication failed or invalid bearer token |
| 403 | Access denied or disabled endpoint |
| 404 | Registration or activation record not found |
| 409 | Database constraint or concurrent update conflict |
| 503 | Email delivery failed |
| 500 | Unexpected server error |

Authentication failure example: `401 Unauthorized`, `application/problem+json`.

```json
{
  "type": "about:blank",
  "title": "Unauthorized",
  "status": 401,
  "detail": "Authentication failed. Please check your credentials or sign in again.",
  "instance": "/api/auth"
}
```

Token exchange or refresh failure example: `400 Bad Request`, `application/json`.

```json
{
  "error": "invalid_grant"
}
```

Token endpoint errors retain the OAuth JSON format, such as `{"error":"invalid_grant"}`. They normally use status 400, with 401 for `invalid_client` and 500 for `server_error`.

## Tests

From the project root:

```bash
# Authorization server tests only
mvn -pl auth-server clean test

# All modules
mvn clean test
```

The current tests use mocks and test security configurations; they do not require running PostgreSQL, SMTP, or Kafka. They do not replace integration testing against the Docker services.

## Current limitations

- OAuth authorizations and registered clients are held in memory, and a new RSA signing key is generated on each application startup. Restarting the server invalidates previously issued tokens. Persistent authorization storage and signing keys are needed before running multiple instances.
- Kafka connection settings exist, but event listener classes are placeholders. Registration approval, account status events, management activation creation, and event publishing are not implemented.
- Public API routes use a broad `/api/**` permit rule after the dedicated logout and internal security chains. New sensitive endpoints need explicit authorization rules.
- Password initialization currently requires a nonblank password; a stronger password policy is not implemented.

Further examples: [authentication](../readme/authentication-endpoint.md), [token endpoint](../readme/token-endpoint.md), [logout](../readme/logout.md), and [administrator bootstrap](../readme/administrator-bootstrap.md).
