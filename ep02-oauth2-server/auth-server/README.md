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

## Database information

### Connection and ownership

| Setting | Local development value | Definition |
| --- | --- | --- |
| Engine | PostgreSQL 17 | Database server provided by Docker Compose |
| Host and port | `localhost:5432` | Connection from applications running on the host |
| Database | `wallet_demo` | Shared development database with separate service schemas |
| Schema | `auth` | Tables owned by the authorization server |
| Database role | `authuser` | Login role and owner of the auth schema and its tables |
| Password | `authpass` | Development password defined in `01-init.sql` |
| JDBC URL | `jdbc:postgresql://localhost:5432/wallet_demo?currentSchema=auth` | Selects the database and default schema |
| Hibernate DDL mode | `validate` | Checks entity mappings against existing tables |

The JDBC connection's `currentSchema=auth` lets Spring Security's JDBC repositories resolve their unqualified table names. Hibernate also uses `hibernate.default_schema=auth`. The database role has `search_path=auth`, configured by `01-init.sql`.

The other services use the `consumer` and `management` schemas with their own database roles. Resource services validate tokens through the auth-server API rather than querying auth tables directly.

### Tables

All tables below belong to the `auth` schema.

| Table | Primary key | Purpose and important columns |
| --- | --- | --- |
| `identity` | `id` — UUID | Shared login identity. `username` is unique and currently contains an email address. |
| `profile` | `id` — UUID | Per-system credentials and access state: `identity_id`, `system`, `role`, encoded `password`, `status`, and `token_version`. |
| `registration` | `id` — UUID | Consumer signup and email verification: `email`, `registered_at`, `initialized_at`, and embedded OTP fields. |
| `activation` | `id` — UUID | Management activation: `email`, `initialized_at`, and nullable OTP fields before the first code request. |
| `login_ticket` | `id` — UUID | One-time login credentials: unique `token_hash`, `profile_id`, `client_id`, `token_version`, `expires_at`, and `consumed_at`. |
| `oauth2_registered_client` | `id` — VARCHAR(100) | Persistent OAuth client configuration: unique `client_id`, encoded `client_secret`, authentication methods, grant types, scopes, client settings, and token settings. |
| `oauth2_authorization` | `id` — VARCHAR(100) | Issued authorization state: `registered_client_id`, `principal_name`, scopes, serialized attributes, access and refresh tokens, metadata, and expiration timestamps. |

`registration` and `activation` embed the OTP record directly; there is no separate OTP table. Their OTP columns include `otp_code`, `status`, `send_at`, `expire_at`, `checked_at`, `send_count`, and `fail_count`.

The OAuth authorization table retains Spring Security's standard columns for other grant types and ID tokens, although those flows are currently disabled. Column presence does not enable an endpoint or grant.

### Relationships and constraints

| Relationship or constraint | Meaning |
| --- | --- |
| `profile.identity_id` → `identity.id` | One identity can have multiple profiles. |
| Unique `profile(identity_id, system)` | An identity can have only one profile per system. |
| `login_ticket.profile_id` → `profile.id` | Each ticket belongs to one profile. |
| `oauth2_authorization.registered_client_id` → `oauth2_registered_client.id` | Each authorization belongs to a registered client. |
| Unique `identity.username` | Prevents duplicate identities for the same username. |
| Unique `login_ticket.token_hash` | Prevents duplicate stored ticket hashes. |
| Unique `oauth2_registered_client.client_id` | Prevents duplicate public client identifiers. |
| `profile` system/role checks | Require nonblank string IDs; configured role compatibility is enforced by application policy. |

`oauth2_authorization.principal_name` contains the profile UUID as text, but has no database foreign key to `profile`. Likewise, `login_ticket.client_id` has no foreign key to the client table. Registration and activation records refer to email addresses rather than identity/profile foreign keys.

### Stored identifiers and status values

Systems and roles are configured strings; lifecycle and OTP statuses still use ordinal `SMALLINT` values. Keep status enum ordering and SQL constraints consistent when changing them.

| Column | Stored values |
| --- | --- |
| `profile.system` | Configured string ID, such as `Consumer` or `Management` |
| `profile.role` | Configured role string, such as `Consumer`, `Management`, or `Administrator` |
| `profile.status` | `0` = Pending, `1` = Active, `2` = Locked, `3` = Closed |
| `registration.status`, `activation.status` | OTP state: `0` = Send, `1` = Success, `2` = Fail |

### Security data and timestamps

Passwords, client secrets, and OTP codes are stored as BCrypt hashes. Login tickets are stored as SHA-256 hashes; the raw ticket is returned only when issued. OAuth access and refresh token values are persisted by the JDBC authorization service and must be treated as sensitive credentials.

`profile.token_version` supports logout across devices for that profile. Tickets capture the version at issuance, and issued access-token metadata includes the version for live validation.

Registration and activation timestamps use `TIMESTAMP WITHOUT TIME ZONE` and Java `LocalDateTime`. Login-ticket and OAuth timestamps use `TIMESTAMP WITH TIME ZONE` for absolute instants. OAuth token values and serialized authorization metadata use PostgreSQL `TEXT`, replacing the generic Spring SQL scripts' `BLOB` columns.

### Initialization and inspection

[`01-init.sql`](../postgres/01-init.sql) creates database roles, schemas, permissions, and default search paths. [`02-auth-schema.sql`](../postgres/02-auth-schema.sql) creates all seven auth tables as `authuser`. Docker executes these scripts only when initializing an empty PostgreSQL volume; editing a script does not change an existing database.

Inspect the schema from the project root:

```bash
docker compose exec postgres psql -U authuser -d wallet_demo
```

Then run these commands inside `psql`:

```text
\dt auth.*
\d auth.profile
\d auth.oauth2_registered_client
\d auth.oauth2_authorization
```

For schema changes during development, use the volume reset described under **Run locally**. No migration files or Hibernate table creation are used. Database backup, expired-record cleanup, and persistent signing-key storage remain deployment tasks; RSA signing keys are not stored in these tables.

## Persistent clients and authorizations

Client IDs are map keys under `app.auth.clients`:

```yaml
app:
  auth:
    clients:
      consumer-demo:
        system: Consumer
        scopes: [consumer.api]
        audiences: [consumer.api]
        secret: ${CONSUMER_CLIENT_SECRET:consumer-demo-secret}
      management-demo:
        system: Management
        scopes: [management.api]
        audiences: [management.api]
        secret: ${MANAGEMENT_CLIENT_SECRET:management-demo-secret}
```

`JdbcRegisteredClientRepository` stores registrations in `auth.oauth2_registered_client`. At startup, configured entries are inserted or updated, preserving existing database IDs and unchanged encoded secrets. Each client explicitly defines its system, scopes, and audiences; token lifetimes use the shared duration properties. The previous separate `client-secrets` map is replaced by each client's `secret` field.

`JdbcOAuth2AuthorizationService` stores issued token state in `auth.oauth2_authorization`. All auth-server instances must use the same database. Restarting the process no longer discards authorization records. The JDBC serialization configuration explicitly allows the `Long` type used by `token_version` while retaining Spring Security's restricted type validation.

Removing a client from YAML does not delete its database registration or previously issued authorizations. Explicitly disable/remove these records when retiring a client. Startup synchronization is intended for coordinated deployment; avoid concurrent first-time seeding of the same new client.

The tables are defined directly in `postgres/02-auth-schema.sql`. Recreate your development database with the documented volume reset workflow to apply them; no migrations or automatic table creation are used. Tokens and authorization metadata are sensitive data; restrict database access and define expired-authorization cleanup before production.

## How to add a new system

This example adds a Merchant system and a Merchant resource service. System and role IDs are case-sensitive strings. Adding configured identifiers requires no Java enum changes or new database columns once the current string-based schema is installed.

### 1. Define the system, roles, client, and resource

Merge the following entries into [`application.yml`](src/main/resources/application.yml), retaining the existing Consumer and Management definitions. Do not create duplicate `app` or `auth` YAML keys.

```yaml
app:
  auth:
    systems:
      Merchant:
        roles: [MerchantOperator, MerchantAdministrator]
    clients:
      merchant-backend:
        system: Merchant
        secret: ${MERCHANT_CLIENT_SECRET}
        scopes: [merchant.api]
        audiences: [merchant.api]
    resources:
      merchant-service:
        secret: ${MERCHANT_RESOURCE_SECRET}
        audiences: [merchant.api]
```

| Property | Sample value | Definition |
| --- | --- | --- |
| `systems` map key | `Merchant` | Profile system identifier stored in `profile.system`. |
| `systems.Merchant.roles` | `[MerchantOperator, MerchantAdministrator]` | Roles permitted for profiles in this system. |
| `clients` map key | `merchant-backend` | OAuth client ID used at `/api/auth` and `/oauth2/token`. |
| `clients.merchant-backend.system` | `Merchant` | Profile system authenticated for this client. |
| `clients.merchant-backend.secret` | `${MERCHANT_CLIENT_SECRET}` | Client secret for HTTP Basic token requests; keep it in a trusted backend. |
| `clients.merchant-backend.scopes` | `[merchant.api]` | Permissions issued to this client by the login-ticket grant. |
| `clients.merchant-backend.audiences` | `[merchant.api]` | APIs for which the issued access token is intended. |
| `resources` map key | `merchant-service` | Resource-server username for internal introspection. |
| `resources.merchant-service.secret` | `${MERCHANT_RESOURCE_SECRET}` | Resource-server credential, separate from the OAuth client secret. |
| `resources.merchant-service.audiences` | `[merchant.api]` | Token audiences this resource registration may introspect. |

Scopes and audiences are explicit lists and need not match the system name. The login-ticket grant issues the client's configured scopes; requesting a narrower scope is not currently implemented. The previous separate resource-secret and resource-audience maps have been replaced by `app.auth.resources`.

### 2. Set secrets and restart the auth server

Set these variables in the environment used to launch the auth server:

```bash
export MERCHANT_CLIENT_SECRET='replace-with-a-strong-client-secret'
export MERCHANT_RESOURCE_SECRET='replace-with-a-different-strong-resource-secret'
mvn -pl auth-server spring-boot:run
```

Stop an already running auth-server process before relaunching. On startup, configured clients are synchronized into `auth.oauth2_registered_client`. A missing/unknown system, empty client scopes/audiences, or an invalid configured onboarding role prevents startup. New resource credentials are loaded at startup too.

### 3. Create a profile for the new system

Configuration registers the system and application; it does not create users. Before authentication, the identity must have a profile with these values:

| Field | Sample value | Requirement |
| --- | --- | --- |
| `identity` | Identity for `merchant@example.com` | New or existing identity; one identity may have profiles in multiple systems. |
| `system` | `Merchant` | Must exactly match the configured system ID. |
| `role` | `MerchantOperator` | Must belong to `systems.Merchant.roles`. |
| `password` | BCrypt hash | Set through a trusted onboarding flow using the password encoder. |
| `status` | `Active` | Pending, Locked, and Closed profiles cannot authenticate. |

Existing onboarding targets can be changed through properties:

```yaml
app:
  auth:
    registration:
      system: Consumer
      role: Consumer
    activation:
      system: Management
      role: Management
  administrator:
    system: Management
    role: Administrator
```

For example, changing `activation.system` to `Merchant` and `activation.role` to `MerchantOperator` makes the existing activation workflow create Active Merchant profiles. It still requires an existing activation record; Kafka-driven record creation is not implemented. Registration creates Pending profiles. Administrator settings apply only when bootstrapping an empty system.

Each existing workflow has one configured target. Changing its target changes the behavior of that workflow for all users; it does not add a separate Merchant endpoint alongside the existing Consumer or Management endpoint. Supporting simultaneous onboarding flows requires additional implementation.

### 4. Authenticate and exchange the ticket

Once an Active Merchant profile exists:

```bash
curl -X POST http://localhost:8080/api/auth \
  -H 'Content-Type: application/json' \
  -d '{"clientId":"merchant-backend","username":"merchant@example.com","password":"your-password"}'
```

Response: `200 OK`.

```json
{
  "loginTicket": "<one-time-ticket>",
  "expiresIn": 60
}
```

Exchange the ticket using the same client ID:

```bash
curl -X POST http://localhost:8080/oauth2/token \
  -u "merchant-backend:${MERCHANT_CLIENT_SECRET}" \
  --data-urlencode 'grant_type=urn:jdc:params:oauth:grant-type:login-ticket' \
  --data-urlencode 'login_ticket=<one-time-ticket>'
```

Response: `200 OK`.

```json
{
  "access_token": "<access-token-jwt>",
  "token_type": "Bearer",
  "expires_in": 599,
  "refresh_token": "<refresh-token>",
  "scope": "merchant.api"
}
```

The JWT contains `system: Merchant`, `roles: ["MerchantOperator"]`, and audience `merchant.api`. Token lifetimes follow the shared configuration.

### 5. Connect the resource service and verify the token

For a service using the same resource-security configuration as the included services, configure:

```yaml
server:
  port: 8083

auth:
  introspection-uri: http://localhost:8080/api/internal/tokens/introspect
  issuer: http://localhost:8080
  resource-id: merchant-service
  resource-secret: ${MERCHANT_RESOURCE_SECRET}
  audiences: merchant.api
```

The included Consumer and Management services have their own endpoint/scope rules. A new service must implement its endpoints and define appropriate authorization rules, such as requiring `SCOPE_merchant.api`. The existing introspection adapters map any nonblank trusted role to `ROLE_<role>`, including `ROLE_MerchantOperator`, without a hardcoded role list.

Verify the registration directly against the auth server:

```bash
curl -X POST http://localhost:8080/api/internal/tokens/introspect \
  -u "merchant-service:${MERCHANT_RESOURCE_SECRET}" \
  --data-urlencode 'token=<access-token-jwt>'
```

Response: `200 OK` (selected attributes shown).

```json
{
  "active": true,
  "system": "Merchant",
  "roles": ["MerchantOperator"],
  "aud": ["merchant.api"],
  "scope": "merchant.api"
}
```

An inactive token or disallowed audience returns `{"active":false}`. Incorrect resource credentials return `401 Unauthorized`.

Adding only a backend service does not necessarily require a new system or role: register its resource credentials and audiences, and grant the required scopes/audiences to existing clients as appropriate.

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

Register the email, read the six-digit code in Mailpit, verify it, and initialize the password. The registration response's `key` is the UUID used as `{id}` in subsequent requests. With the default onboarding settings, initialization creates a Pending Consumer profile, which cannot authenticate until activated. Automatic approval through Kafka is not implemented yet.

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

Request a code, verify the email, and initialize the password. With the default onboarding settings, initialization creates an Active Management profile and marks the activation record as used. Event-driven activation record creation and activation-link delivery are not implemented yet.

## Resource-server token validation

Resource servers call the custom internal endpoint with their own HTTP Basic credentials:

Request body: `application/x-www-form-urlencoded`. The token and HTTP Basic resource-server authentication are required.

| Parameter Name | Sample Value | Definition |
| --- | --- | --- |
| `token` | `<access-token>` | Form field containing the access token to validate. |
| HTTP Basic username | `management-service` | Resource-server ID configured under `app.auth.resources`. |
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

A valid token returns `active: true` and token attributes. Invalid tokens or disallowed audiences return `{"active":false}`. Resource credentials are separate from OAuth client credentials. Audience permissions are configured under `app.auth.resources.<resource-id>.audiences`; the current Consumer service accepts both `consumer.api` and `management.api`, while Management service accepts `management.api`.

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

The current tests use mocks, test security configurations, and an embedded H2 database for JDBC token persistence; they do not require running PostgreSQL, SMTP, or Kafka. They do not replace integration testing against the Docker services.

## Current limitations

- Client registrations and OAuth authorizations use shared PostgreSQL storage. RSA signing keys are still generated at startup; persistent shared keys and key rotation remain necessary for a production signing-key lifecycle. Current internal introspection validates persisted token records rather than JWT signatures.
- Kafka connection settings exist, but event listener classes are placeholders. Registration approval, account status events, management activation creation, and event publishing are not implemented.
- Public API routes use a broad `/api/**` permit rule after the dedicated logout and internal security chains. New sensitive endpoints need explicit authorization rules.
- Password initialization currently requires a nonblank password; a stronger password policy is not implemented.

Further examples: [authentication](../readme/authentication-endpoint.md), [token endpoint](../readme/token-endpoint.md), [logout](../readme/logout.md), and [administrator bootstrap](../readme/administrator-bootstrap.md).
