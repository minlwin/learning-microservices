# Authorization Server

REST authentication and token service for the Consumer and Management applications. Built with Java 25, Spring Boot, Spring Security Authorization Server, JPA, and PostgreSQL.

Clients use their own login screen, submit credentials to `/api/auth`, and exchange the returned login ticket for tokens. This is a custom login-ticket grant; the ticket is not an OpenID Connect ID token. No hosted login form or redirect flow is enabled.

## Contents

- [Run locally](#run-locally)
- [Configuration](#configuration)
- [Database information](#database-information)
- [Request conventions](#request-conventions)
- [API endpoints](#api-endpoints)
- [Error responses](#error-responses)
- [Implementation notes](#implementation-notes)
- [How to add a new system](#how-to-add-a-new-system)
- [Tests](#tests)
- [Current boundaries](#current-boundaries)

## Run locally

Requirements: Docker with Docker Compose for container mode, or JDK 25 and Maven for host-run development. Run these commands from the project root (`ep02-oauth2-server`):

```bash
# Start all services and infrastructure as containers.
docker compose up --build -d
```

To run this module from Maven while keeping only the infrastructure in Docker, use:

```bash
docker compose stop auth-server
docker compose up -d postgres kafka mailpit
mvn -pl auth-server spring-boot:run
```

The Auth Server container is exposed at `http://localhost:8080`. Its signing keys are stored in the `auth_signing_keys` named volume so tokens remain verifiable after container recreation. The Compose issuer is `http://auth-server:8080` for internal container-to-container calls; host-run mode keeps the `http://localhost:8080` default.

Each service has a separate default HTTP port:

| Service | Default port | Base URL |
| --- | --- | --- |
| auth-server | 8080 | `http://localhost:8080` |
| consumer-service | 8081 | `http://localhost:8081` |
| management-service | 8082 | `http://localhost:8082` |

Each service supports a `SERVER_PORT` override. Set it separately for each process; a shared exported value would assign the same port to every service. If you change the auth-server port, also update `AUTH_ISSUER` in all services, `AUTH_INTROSPECTION_URI` in both resource services, and `AUTH_EMAIL_CHECK_URI` in management-service.

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

Build an executable JAR from the project root:

```bash
mvn -pl auth-server clean package
java -jar auth-server/target/auth-server-0.0.1-SNAPSHOT.jar
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
| `CONSUMER_INVITATION_URL` | `http://localhost:3000/consumer/registration/{id}` | Frontend route for a management-created Consumer invitation |
| `CONSUMER_INVITATION_LIFETIME` | `7d` | Consumer invitation lifetime |
| `AUTH_SIGNING_PRIVATE_KEY_PATH` | `~/.wallet-demo/auth-signing-private.pem` | Persistent RSA signing private key (mount a managed secret in production) |
| `AUTH_SIGNING_PUBLIC_KEY_PATH` | `~/.wallet-demo/auth-signing-public.pem` | Matching RSA public key shared by Auth Server instances |
| `OTP_MAX_SENDS` | `5` | Maximum OTP emails sent per registration or activation |
| `ADMINISTRATOR_USERNAME` | `admin@example.com` | Initial administrator email |
| `ADMINISTRATOR_PASSWORD` | `ChangeThisLocalAdminPassword1` | Initial administrator password (change for production) |
| `CONSUMER_CLIENT_SECRET` | `consumer-demo-secret` | Consumer OAuth client secret |
| `MANAGEMENT_CLIENT_SECRET` | `management-demo-secret` | Management OAuth client secret |
| `CONSUMER_RESOURCE_SECRET` | `consumer-resource-secret` | Consumer service introspection credential |
| `MANAGEMENT_RESOURCE_SECRET` | `management-resource-secret` | Management service introspection credential |
| `MAIL_HOST` / `MAIL_PORT` | `localhost` / `1025` | SMTP connection |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | Empty | SMTP credentials |
| `MAIL_SMTP_AUTH` | `false` | SMTP authentication |
| `MAIL_SMTP_STARTTLS_ENABLE` | `false` | SMTP STARTTLS |
| `MAIL_FROM` | `no-reply@wallet-demo.local` | Sender address |
| `MANAGEMENT_ACTIVATION_URL` | `http://localhost:3000/management/activation/{id}` | Frontend route for a management activation invitation |
| `MANAGEMENT_ACTIVATION_LIFETIME` | `7d` | Lifetime of a management activation invitation |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker |
| `KAFKA_CONSUMER_GROUP_ID` | `auth-server` | Kafka consumer group |
| `KAFKA_OUTBOX_FIXED_DELAY` | `1000` | Delay in milliseconds between outbox publisher polls |
| `CONSUMER_ACCOUNT_APPROVED_TOPIC` | `consumer-account-approved.v1` | Topic consumed after Consumer Service approves an account |
| `CONSUMER_ACCOUNT_APPROVED_GROUP` | `auth-server-consumer-account-approvals` | Consumer approval-event group |
| `CONSUMER_ACCOUNT_REJECTED_TOPIC` | `consumer-account-rejected.v1` | Topic consumed after Consumer Service rejects an account |
| `CONSUMER_ACCOUNT_REJECTED_GROUP` | `auth-server-consumer-account-rejections` | Consumer rejection-event group |
| `PIN_LOCK_SETTING_TOPIC` | `pin-lock-setting-changed.v1` | Topic consumed for PIN lock setting changes |
| `PIN_LOCK_SETTING_CONSUMER_GROUP` | `auth-server-pin-lock-settings` | PIN settings consumer group; instances should share it when using the same auth database |
| `PIN_LOCK_ACTIVITY_TOPIC` | `pin-lock-activity-changed.v1` | Topic published for PIN lock transitions |
| `MANAGEMENT_ACCOUNT_INVITATION_TOPIC` | `management-account-invitation.v1` | Topic consumed for management account invitations and produced by Management Service |
| `MANAGEMENT_ACCOUNT_INVITATION_GROUP` | `auth-server-management-account-invitations` | Management invitation consumer group |
| `MANAGEMENT_ACCOUNT_ACTIVATED_TOPIC` | `management-account-activated.v1` | Topic published after management password initialization and consumed by Management Service |

The `prod` and `production` Spring profiles reject the checked-in development secrets, default administrator username, localhost URLs, and the development signing-key path. Non-production startup creates a stable RSA key pair under `~/.wallet-demo/` if one is not present. For production, provision a private/public PEM key pair and set `AUTH_SIGNING_PRIVATE_KEY_PATH` and `AUTH_SIGNING_PUBLIC_KEY_PATH` to mounted secret files. Keep the same key pair available to every Auth Server instance and use a controlled rotation process. Auth Server publishes the management activation event; its versioned JSON Schema is packaged in [`src/main/resources/contracts/management-account-activated-v1.schema.json`](src/main/resources/contracts/management-account-activated-v1.schema.json). The schema documents the Kafka payload shape for consumers and event evolution; it is not currently enforced automatically at runtime.

Initialization passwords must contain at least 12 characters and fit within BCrypt's 72-byte UTF-8 input limit. OTP codes expire after the configured interval, can be resent only after the configured wait, and are limited to five sends per registration or activation by default (`app.otp.resend.max-sends`).

To provision an RSA signing pair, generate the private key with `openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out auth-signing-private.pem`, then derive the public key with `openssl pkey -in auth-signing-private.pem -pubout -out auth-signing-public.pem`. Restrict access to the private key (for example, `chmod 600 auth-signing-private.pem`), protect it as a secret, and mount both files at the configured paths.

`POSTGRES_PASSWORD` configures the container superuser; it does not change the `authuser` password defined in `01-init.sql`. Keep the SQL and application database credentials consistent when changing them.

Other application settings are configured directly under `app` in YAML:

| Property | Default |
| --- | --- |
| `app.auth.ticket-lifetime-seconds` | 60 seconds |
| `app.auth.management-activation-url` | `http://localhost:3000/management/activation/{id}` |
| `app.auth.management-activation-lifetime` | 7 days |
| `app.otp.expiration.registration` | 3 minutes |
| `app.otp.expiration.activation` | 5 minutes |
| `app.otp.resend.interval-seconds` | 60 seconds |
| `app.otp.resend.max-sends` | 5 OTP sends per registration or activation |
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

Custom auth-server settings are bound through `AuthenticationProps`, `AdministratorProps`, `OtpCodeProps`, `MailConfigProps`, and `KafkaProps`. `PropertiesConfig` enables mail/OTP property binding; `KafkaProps` binds `app.kafka.pin-lock` for the topic and consumer group and `app.kafka.outbox.fixed-delay` for outbox polling. Standard datasource, SMTP, and Kafka client settings remain under `spring.*` and use Spring Boot auto-configuration.

The service has a generic outbox table, repository, and scheduled `OutboxEventPublisher`. `PinLockActivityService` enqueues PIN lock transition events in the same transaction as state changes. Other Auth Server business workflows can use the same outbox infrastructure. Polling cadence defaults to one second and can be changed with `KAFKA_OUTBOX_FIXED_DELAY`.

#### Recovering dead-lettered outbox rows

After correcting the underlying Kafka or SMTP problem, an operator can replay a specific dead-lettered row by reviewing its `last_error` and resetting its retry state. Use a database transaction and the event UUID to avoid replaying unrelated messages:

```sql
UPDATE auth.outbox_event
SET dead_lettered_at = NULL, attempt_count = 0, last_error = NULL,
    next_attempt_at = CURRENT_TIMESTAMP, claimed_until = NULL, claim_token = NULL
WHERE id = '<event-uuid>' AND dead_lettered_at IS NOT NULL;

UPDATE auth.email_outbox_event
SET dead_lettered_at = NULL, attempt_count = 0, last_error = NULL,
    next_attempt_at = CURRENT_TIMESTAMP, claimed_until = NULL, claim_token = NULL
WHERE id = '<event-uuid>' AND dead_lettered_at IS NOT NULL;
```

Mail uses MIME messages with HTML content. Development credentials are examples; replace them for deployed environments. Client secrets belong in a trusted backend and must not be embedded in browser or mobile application code.

| Configuration | Owner |
| --- | --- |
| Authorization endpoint chain, JDBC OAuth storage, token generation | `AuthorizationServerConfig` |
| Bearer logout and internal resource authentication | `SessionSecurityConfig` |
| Public API access, disabled endpoint handling, password encoder | `SecurityConfig` |
| JPA datasource and transactions, SMTP sender, Kafka factories/template | Spring Boot auto-configuration from `spring.*` |
| Mail and OTP property registration | `PropertiesConfig` |

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
| `activation` | `id` — UUID | Management activation keyed by the Management Service account UUID: `email`, `invitation_expires_at`, `initialized_at`, and nullable OTP fields before the first code request. |
| `email_outbox_event` | `id` — UUID | Retryable invitation emails for Consumer registration and Management activation; includes recipient, system, link, and send status. |
| `login_ticket` | `id` — UUID | One-time login credentials: unique `token_hash`, `profile_id`, `client_id`, `token_version`, `expires_at`, and `consumed_at`. |
| `pin_lock_setting` | `system` — VARCHAR(255) | Latest setting snapshot replicated from Management Service, including version and deleted flag. |
| `pin_lock_state` | `profile_id` — UUID | Current PIN lock status and failed-attempt count for a system-specific profile. Kept separate from `profile.status`. |
| `pin_lock_history` | `id` — UUID | Audit records for PIN lock, expiry, and reset transitions. |
| `outbox_event` | `id` — UUID | Durable event payloads waiting to be published, with publish and retry status. |
| `oauth2_registered_client` | `id` — VARCHAR(100) | Persistent OAuth client configuration: unique `client_id`, encoded `client_secret`, authentication methods, grant types, scopes, client settings, and token settings. |
| `oauth2_authorization` | `id` — VARCHAR(100) | Issued authorization state: `registered_client_id`, `principal_name`, scopes, serialized attributes, access and refresh tokens, metadata, and expiration timestamps. |

`registration` and `activation` embed the OTP record directly; there is no separate OTP table. Their OTP columns include `otp_code`, `status`, `send_at`, `expire_at`, `checked_at`, `send_count`, and `fail_count`.

The OAuth authorization table retains Spring Security's standard columns for other grant types and ID tokens, although those flows are currently disabled. Column presence does not enable an endpoint or grant.

### Table definitions

Column names and PostgreSQL types below follow the initialization SQL. **Nullable** describes database nullability. **Default** is the SQL default, not a Java initializer or service-assigned value; `—` means no explicit SQL default. PK = primary key, FK = foreign key. Primary-key columns are implicitly NOT NULL.

#### `auth.identity`

| Column | PostgreSQL Type | Nullable | Default | Key / Constraints | Definition |
| --- | --- | --- | --- | --- | --- |
| `id` | `UUID` | No | — | PK | Record UUID generated by Hibernate; no database UUID default. |
| `username` | `VARCHAR(255)` | No | — | UNIQUE | Unique identity username; currently an email address. |

#### `auth.profile`

| Column | PostgreSQL Type | Nullable | Default | Key / Constraints | Definition |
| --- | --- | --- | --- | --- | --- |
| `id` | `UUID` | No | — | PK | Record UUID generated by Hibernate; no database UUID default. |
| `identity_id` | `UUID` | No | — | UNIQUE (identity_id, system); FK → auth.identity.id | Identity owning this per-system profile. |
| `system` | `VARCHAR(255)` | No | — | UNIQUE (identity_id, system); CHECK (length(trim(system)) > 0) | Case-sensitive system ID from app.auth.systems. |
| `role` | `VARCHAR(255)` | No | — | CHECK (length(trim(role)) > 0) | Configured role compatible with the profile system. |
| `password` | `VARCHAR(255)` | No | — | — | BCrypt-encoded profile password. |
| `status` | `SMALLINT` | No | — | CHECK (status BETWEEN 0 AND 3) | Profile state: 0 Pending, 1 Active, 2 Locked, 3 Closed. |
| `token_version` | `BIGINT` | No | `0` | — | Profile revocation version; incremented to invalidate existing tokens and tickets. |

Table constraints and indexes:

| Name | Definition |
| --- | --- |
| `uk_profile_identity_system` | `UNIQUE (identity_id, system)` |
| `fk_profile_identity` | `FOREIGN KEY (identity_id) REFERENCES auth.identity (id)` |
| `ck_profile_system` | `CHECK (length(trim(system)) > 0)` |
| `ck_profile_role` | `CHECK (length(trim(role)) > 0)` |
| `ck_profile_status` | `CHECK (status BETWEEN 0 AND 3)` |

#### `auth.pin_lock_setting`

| Column | PostgreSQL Type | Nullable | Default | Key / Constraints | Definition |
| --- | --- | --- | --- | --- | --- |
| `system` | `VARCHAR(255)` | No | — | PK | Configured system identifier, such as Consumer. |
| `temp_lock_fails_count` | `INTEGER` | No | — | — | Failed-PIN threshold for a temporary lock. |
| `temp_lock_wait_minutes` | `INTEGER` | No | — | — | Temporary-lock duration in minutes. |
| `permanent_lock_fails_count` | `INTEGER` | No | — | — | Permanent-lock failure threshold. |
| `deleted` | `BOOLEAN` | No | — | — | Soft-delete state mirrored from Management Service. |
| `version` | `BIGINT` | No | — | — | Management Service version used to ignore duplicate or older events. |

#### `auth.pin_lock_state`

| Column | PostgreSQL Type | Nullable | Default | Key / Constraints | Definition |
| --- | --- | --- | --- | --- | --- |
| `profile_id` | `UUID` | No | — | PK; FK → auth.profile.id | Profile whose PIN lock state is tracked. One row per profile, created when lock tracking begins. |
| `status` | `VARCHAR(32)` | No | — | CHECK | `Active`, `TemporarilyLocked`, or `PermanentlyLocked`; independent of the profile's general status. |
| `failed_attempt_count` | `INTEGER` | No | — | CHECK (failed_attempt_count >= 0) | Failed PIN attempts since the last successful PIN verification; used for the permanent-lock threshold. |
| `failed_since_temporary_lock` | `INTEGER` | No | — | CHECK (failed_since_temporary_lock >= 0) | Failed attempts since the last temporary lock; reset when a temporary lock is applied. |
| `locked_until` | `TIMESTAMP(6) WITH TIME ZONE` | Yes | — | — | Expiration instant for a temporary lock; null for an active or permanent state. |
| `last_failed_at` | `TIMESTAMP(6) WITH TIME ZONE` | Yes | — | — | Instant of the latest failed PIN verification. |
| `status_changed_at` | `TIMESTAMP(6) WITH TIME ZONE` | No | — | — | Instant when the current status was last changed. |
| `setting_version` | `BIGINT` | No | — | — | PIN lock setting version used for the latest decision. |
| `version` | `BIGINT` | No | — | — | JPA optimistic-lock version. |

#### `auth.pin_lock_history`

| Column | PostgreSQL Type | Nullable | Default | Key / Constraints | Definition |
| --- | --- | --- | --- | --- | --- |
| `id` | `UUID` | No | — | PK | History record UUID generated by Hibernate. |
| `profile_id` | `UUID` | No | — | FK → auth.profile.id | Profile whose PIN lock changed. |
| `action` | `VARCHAR(32)` | No | — | CHECK | `TemporarilyLocked`, `PermanentlyLocked`, `TemporaryLockExpired`, or `PermanentUnlocked`. |
| `failed_attempt_count` | `INTEGER` | No | — | CHECK (failed_attempt_count >= 0) | Failed-attempt count at the time of the recorded transition. |
| `locked_until` | `TIMESTAMP(6) WITH TIME ZONE` | Yes | — | — | Temporary lock expiry when applicable. |
| `setting_version` | `BIGINT` | No | — | — | Policy version used for the recorded transition. |
| `requested_by` | `VARCHAR(255)` | Yes | — | — | Management principal for an administrative unlock. |
| `occurred_at` | `TIMESTAMP(6) WITH TIME ZONE` | No | — | — | Time the transition was recorded. |

Table constraints and indexes:

| Name | Definition |
| --- | --- |
| `ck_pin_lock_state_status` | `CHECK (status IN ('Active', 'TemporarilyLocked', 'PermanentlyLocked'))` |
| `ck_pin_lock_state_failed_attempt_count` | `CHECK (failed_attempt_count >= 0)` |
| `ck_pin_lock_state_failed_since_temp` | `CHECK (failed_since_temporary_lock >= 0)` |
| `ck_pin_lock_state_locked_until` | Temporary status requires `locked_until`; other statuses require null. |
| `ck_pin_lock_history_action` | `CHECK (action IN ('TemporarilyLocked', 'PermanentlyLocked', 'TemporaryLockExpired', 'PermanentUnlocked'))` |
| `ix_pin_lock_history_profile_occurred_at` | `INDEX (profile_id, occurred_at)` |

#### `auth.outbox_event`

| Column | PostgreSQL Type | Nullable | Default | Key / Constraints | Definition |
| --- | --- | --- | --- | --- | --- |
| `id` | `UUID` | No | — | PK | Event UUID, assigned by the event producer. |
| `topic` | `VARCHAR(100)` | No | — | — | Kafka topic for this event. |
| `aggregate_key` | `VARCHAR(255)` | No | — | — | Kafka message key, generally the aggregate identifier. |
| `payload` | `TEXT` | No | — | — | Serialized event payload. |
| `created_at` | `TIMESTAMP(6) WITH TIME ZONE` | No | — | — | When the event was enqueued. |
| `next_attempt_at` | `TIMESTAMP(6) WITH TIME ZONE` | No | `CURRENT_TIMESTAMP` | — | Earliest instant for the next publish attempt. |
| `claimed_until` | `TIMESTAMP(6) WITH TIME ZONE` | Yes | — | — | Lease expiry for a publisher claim. |
| `claim_token` | `UUID` | Yes | — | — | Claim owner token used to prevent stale workers from completing a newer claim. |
| `published_at` | `TIMESTAMP(6) WITH TIME ZONE` | Yes | — | — | Set after Kafka acknowledges publication. |
| `attempt_count` | `INTEGER` | No | — | — | Number of failed publication attempts. |
| `last_error` | `TEXT` | Yes | — | — | Most recent publication error, if any. |
| `dead_lettered_at` | `TIMESTAMP(6) WITH TIME ZONE` | Yes | — | — | Set after the final failed attempt; the row remains for investigation but is excluded from polling. |

#### `auth.registration`

| Column | PostgreSQL Type | Nullable | Default | Key / Constraints | Definition |
| --- | --- | --- | --- | --- | --- |
| `id` | `UUID` | No | — | PK | Record UUID generated by Hibernate; no database UUID default. |
| `email` | `VARCHAR(255)` | No | — | — | Email associated with this record. |
| `registered_at` | `TIMESTAMP(6) WITH TIME ZONE` | No | — | — | Registration instant. |
| `initialized_at` | `TIMESTAMP(6) WITH TIME ZONE` | Yes | — | — | Password-initialization instant; null until initialization succeeds. |
| `otp_code` | `VARCHAR(255)` | No | — | — | BCrypt-encoded email verification code. |
| `status` | `SMALLINT` | No | — | CHECK (status BETWEEN 0 AND 2) | OTP state: 0 Send, 1 Success, 2 Fail. |
| `send_at` | `TIMESTAMP(6) WITH TIME ZONE` | No | — | — | Instant when the current OTP was sent. |
| `expire_at` | `TIMESTAMP(6) WITH TIME ZONE` | No | — | — | Expiration instant for the current OTP. |
| `checked_at` | `TIMESTAMP(6) WITH TIME ZONE` | Yes | — | — | Latest OTP verification attempt instant. |
| `send_count` | `INTEGER` | No | — | — | Number of OTP sends recorded for this request. |
| `fail_count` | `INTEGER` | No | — | — | Failed OTP verification attempt count used by cooldown checks. |

Table constraints and indexes:

| Name | Definition |
| --- | --- |
| `ck_registration_otp_status` | `CHECK (status BETWEEN 0 AND 2)` |

#### `auth.activation`

| Column | PostgreSQL Type | Nullable | Default | Key / Constraints | Definition |
| --- | --- | --- | --- | --- | --- |
| `id` | `UUID` | No | — | PK | Management account UUID carried by the invitation event; correlates activation with the local account. |
| `email` | `VARCHAR(255)` | No | — | — | Email associated with this record. |
| `invitation_expires_at` | `TIMESTAMP(6) WITH TIME ZONE` | No | — | — | Expiration instant for this management activation invitation. |
| `initialized_at` | `TIMESTAMP(6) WITH TIME ZONE` | Yes | — | — | Password-initialization instant; null until initialization succeeds. |
| `otp_code` | `VARCHAR(255)` | Yes | — | — | BCrypt-encoded email verification code. |
| `status` | `SMALLINT` | Yes | — | CHECK (status BETWEEN 0 AND 2) | OTP state: 0 Send, 1 Success, 2 Fail. |
| `send_at` | `TIMESTAMP(6) WITH TIME ZONE` | Yes | — | — | Instant when the current OTP was sent. |
| `expire_at` | `TIMESTAMP(6) WITH TIME ZONE` | Yes | — | — | Expiration instant for the current OTP. |
| `checked_at` | `TIMESTAMP(6) WITH TIME ZONE` | Yes | — | — | Latest OTP verification attempt instant. |
| `send_count` | `INTEGER` | Yes | `0` | — | Number of OTP sends recorded for this request. |
| `fail_count` | `INTEGER` | Yes | `0` | — | Failed OTP verification attempt count used by cooldown checks. |

Table constraints and indexes:

| Name | Definition |
| --- | --- |
| `ck_activation_otp_status` | `CHECK (status BETWEEN 0 AND 2)` |

#### `auth.email_outbox_event`

| Column | PostgreSQL Type | Nullable | Default | Key / Constraints | Definition |
| --- | --- | --- | --- | --- | --- |
| `id` | `UUID` | No | — | PK | Email event ID used to avoid duplicate invitation rows. |
| `recipient` | `VARCHAR(255)` | No | — | — | Destination email address. |
| `system` | `VARCHAR(32)` | No | — | — | Invitation type: Consumer or Management. |
| `invitation_url` | `VARCHAR(2048)` | No | — | — | Link inserted into the invitation email. |
| `created_at` | `TIMESTAMP(6) WITH TIME ZONE` | No | — | — | Email event creation instant. |
| `next_attempt_at` | `TIMESTAMP(6) WITH TIME ZONE` | No | `CURRENT_TIMESTAMP` | — | Earliest instant for the next delivery attempt. |
| `claimed_until` | `TIMESTAMP(6) WITH TIME ZONE` | Yes | — | — | Lease expiry for a mail publisher claim. |
| `claim_token` | `UUID` | Yes | — | — | Claim owner token used to prevent stale workers from completing a newer claim. |
| `sent_at` | `TIMESTAMP(6) WITH TIME ZONE` | Yes | — | — | Successful delivery instant; null while pending or retrying. |
| `attempt_count` | `INTEGER` | No | `0` | — | Number of delivery failures. |
| `last_error` | `TEXT` | Yes | — | — | Most recent delivery error. |
| `dead_lettered_at` | `TIMESTAMP(6) WITH TIME ZONE` | Yes | — | — | Set after the final failed attempt; the row remains for investigation but is excluded from polling. |

#### `auth.login_ticket`

| Column | PostgreSQL Type | Nullable | Default | Key / Constraints | Definition |
| --- | --- | --- | --- | --- | --- |
| `id` | `UUID` | No | — | PK | Record UUID generated by Hibernate; no database UUID default. |
| `token_hash` | `VARCHAR(64)` | No | — | UNIQUE | SHA-256 hash of the one-time ticket; raw tickets are not persisted. |
| `profile_id` | `UUID` | No | — | FK → auth.profile.id | Profile authenticated when the ticket was issued. |
| `client_id` | `VARCHAR(255)` | No | — | — | OAuth client ID bound to this ticket; no database foreign key. |
| `token_version` | `BIGINT` | No | `0` | — | Profile token version captured when the ticket was issued. |
| `expires_at` | `TIMESTAMP(6) WITH TIME ZONE` | No | — | — | Instant after which the ticket cannot be exchanged. |
| `consumed_at` | `TIMESTAMP(6) WITH TIME ZONE` | Yes | — | — | Successful exchange instant; null until consumed. |

#### `auth.oauth2_registered_client`

| Column | PostgreSQL Type | Nullable | Default | Key / Constraints | Definition |
| --- | --- | --- | --- | --- | --- |
| `id` | `VARCHAR(100)` | No | — | PK | Internal registered-client identifier; referenced by OAuth authorizations. |
| `client_id` | `VARCHAR(100)` | No | — | UNIQUE index | Public OAuth client identifier; unique index enforces uniqueness. |
| `client_id_issued_at` | `TIMESTAMP WITH TIME ZONE` | No | `CURRENT_TIMESTAMP` | — | Instant when the client identifier was issued. |
| `client_secret` | `VARCHAR(200)` | Yes | `NULL` | — | Encoded client secret; current configured clients use BCrypt. |
| `client_secret_expires_at` | `TIMESTAMP WITH TIME ZONE` | Yes | `NULL` | — | Optional client-secret expiry instant. |
| `client_name` | `VARCHAR(200)` | No | — | — | Human-readable client name. |
| `client_authentication_methods` | `VARCHAR(1000)` | No | — | — | Serialized comma-separated allowed client authentication methods. |
| `authorization_grant_types` | `VARCHAR(1000)` | No | — | — | Serialized comma-separated allowed grant types. |
| `redirect_uris` | `VARCHAR(1000)` | Yes | `NULL` | — | Serialized redirect URIs; redirect flows are disabled in this application. |
| `post_logout_redirect_uris` | `VARCHAR(1000)` | Yes | `NULL` | — | Serialized post-logout redirect URIs; hosted OIDC logout is disabled. |
| `scopes` | `VARCHAR(1000)` | No | — | — | Serialized comma-separated registered scopes. |
| `client_settings` | `VARCHAR(2000)` | No | — | — | Serialized client configuration JSON. |
| `token_settings` | `VARCHAR(2000)` | No | — | — | Serialized token configuration JSON, including lifetimes. |

Table constraints and indexes:

| Name | Definition |
| --- | --- |
| `Unnamed table constraint` | `PRIMARY KEY (id)` |
| `uk_oauth2_registered_client_client_id` | `UNIQUE INDEX (client_id)` |

#### `auth.oauth2_authorization`

| Column | PostgreSQL Type | Nullable | Default | Key / Constraints | Definition |
| --- | --- | --- | --- | --- | --- |
| `id` | `VARCHAR(100)` | No | — | PK | Internal authorization identifier. |
| `registered_client_id` | `VARCHAR(100)` | No | — | FK → auth.oauth2_registered_client.id | Internal registered-client ID; references oauth2_registered_client.id. |
| `principal_name` | `VARCHAR(200)` | No | — | — | Authenticated profile UUID stored as text; no profile foreign key. |
| `authorization_grant_type` | `VARCHAR(100)` | No | — | — | Grant type used to create this authorization. |
| `authorized_scopes` | `VARCHAR(1000)` | Yes | `NULL` | — | Serialized comma-separated authorized scopes. |
| `attributes` | `TEXT` | Yes | `NULL` | — | Serialized authorization attributes. |
| `state` | `VARCHAR(500)` | Yes | `NULL` | — | Authorization state retained by the standard JDBC schema; redirect flow is disabled. |
| `authorization_code_value` | `TEXT` | Yes | `NULL` | — | Authorization code value; treat as a sensitive credential. Retained for schema compatibility; this flow is disabled. |
| `authorization_code_issued_at` | `TIMESTAMP WITH TIME ZONE` | Yes | `NULL` | — | Authorization code issuance instant. Retained for schema compatibility; this flow is disabled. |
| `authorization_code_expires_at` | `TIMESTAMP WITH TIME ZONE` | Yes | `NULL` | — | Authorization code expiry instant. Retained for schema compatibility; this flow is disabled. |
| `authorization_code_metadata` | `TEXT` | Yes | `NULL` | — | Serialized authorization code metadata. Retained for schema compatibility; this flow is disabled. |
| `access_token_value` | `TEXT` | Yes | `NULL` | — | Access token value; treat as a sensitive credential. |
| `access_token_issued_at` | `TIMESTAMP WITH TIME ZONE` | Yes | `NULL` | — | Access token issuance instant. |
| `access_token_expires_at` | `TIMESTAMP WITH TIME ZONE` | Yes | `NULL` | — | Access token expiry instant. |
| `access_token_metadata` | `TEXT` | Yes | `NULL` | — | Serialized access token metadata. |
| `access_token_type` | `VARCHAR(100)` | Yes | `NULL` | — | Access-token type, such as Bearer. |
| `access_token_scopes` | `VARCHAR(1000)` | Yes | `NULL` | — | Serialized comma-separated access-token scopes. |
| `oidc_id_token_value` | `TEXT` | Yes | `NULL` | — | OIDC ID token value; treat as a sensitive credential. Retained for schema compatibility; this flow is disabled. |
| `oidc_id_token_issued_at` | `TIMESTAMP WITH TIME ZONE` | Yes | `NULL` | — | OIDC ID token issuance instant. Retained for schema compatibility; this flow is disabled. |
| `oidc_id_token_expires_at` | `TIMESTAMP WITH TIME ZONE` | Yes | `NULL` | — | OIDC ID token expiry instant. Retained for schema compatibility; this flow is disabled. |
| `oidc_id_token_metadata` | `TEXT` | Yes | `NULL` | — | Serialized oidc id token metadata. Retained for schema compatibility; this flow is disabled. |
| `refresh_token_value` | `TEXT` | Yes | `NULL` | — | Refresh token value; treat as a sensitive credential. |
| `refresh_token_issued_at` | `TIMESTAMP WITH TIME ZONE` | Yes | `NULL` | — | Refresh token issuance instant. |
| `refresh_token_expires_at` | `TIMESTAMP WITH TIME ZONE` | Yes | `NULL` | — | Refresh token expiry instant. |
| `refresh_token_metadata` | `TEXT` | Yes | `NULL` | — | Serialized refresh token metadata. |
| `user_code_value` | `TEXT` | Yes | `NULL` | — | Device-flow user code value; treat as a sensitive credential. Retained for schema compatibility; this flow is disabled. |
| `user_code_issued_at` | `TIMESTAMP WITH TIME ZONE` | Yes | `NULL` | — | Device-flow user code issuance instant. Retained for schema compatibility; this flow is disabled. |
| `user_code_expires_at` | `TIMESTAMP WITH TIME ZONE` | Yes | `NULL` | — | Device-flow user code expiry instant. Retained for schema compatibility; this flow is disabled. |
| `user_code_metadata` | `TEXT` | Yes | `NULL` | — | Serialized device-flow user code metadata. Retained for schema compatibility; this flow is disabled. |
| `device_code_value` | `TEXT` | Yes | `NULL` | — | Device code value; treat as a sensitive credential. Retained for schema compatibility; this flow is disabled. |
| `device_code_issued_at` | `TIMESTAMP WITH TIME ZONE` | Yes | `NULL` | — | Device code issuance instant. Retained for schema compatibility; this flow is disabled. |
| `device_code_expires_at` | `TIMESTAMP WITH TIME ZONE` | Yes | `NULL` | — | Device code expiry instant. Retained for schema compatibility; this flow is disabled. |
| `device_code_metadata` | `TEXT` | Yes | `NULL` | — | Serialized device code metadata. Retained for schema compatibility; this flow is disabled. |

Table constraints and indexes:

| Name | Definition |
| --- | --- |
| `Unnamed table constraint` | `PRIMARY KEY (id)` |
| `fk_oauth2_authorization_client` | `FOREIGN KEY (registered_client_id) REFERENCES auth.oauth2_registered_client (id)` |

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

Event timestamps across all services use Java `Instant` and PostgreSQL `TIMESTAMP WITH TIME ZONE` to represent absolute instants. OTP expiration and cooldown calculations use elapsed durations; retry timestamps in messages use ISO-8601 UTC (the `Z` suffix). Date-only fields, such as date of birth, remain `LocalDate` / `DATE`. OAuth token values and serialized authorization metadata use PostgreSQL `TEXT`, replacing the generic Spring SQL scripts' `BLOB` columns.

### Initialization and inspection

[`01-init.sql`](../postgres/01-init.sql) creates database roles, schemas, permissions, and default search paths. [`02-auth-schema.sql`](../postgres/02-auth-schema.sql) creates the auth tables, including PIN lock state and history, as `authuser`. Docker executes initialization scripts only for an empty PostgreSQL volume; after schema changes during development, recreate the local volume so the scripts run again.

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

For schema changes during development, use the volume reset described under **Run locally**. No migration files or Hibernate table creation are used. RSA signing keys are loaded from PEM files outside PostgreSQL; production deployments must provision, protect, share, back up, and rotate those files independently of the database.

## Request conventions

Default base URL: `http://localhost:8080`. Examples assume the default configuration and illustrative identifiers/tokens. JSON requests use `Content-Type: application/json`; token exchange, refresh, and introspection use form URL encoding. UUIDs are strings, event timestamps are UTC ISO-8601 instants, and token claim times are Unix seconds. Empty responses contain no JSON.

```bash
export BASE_URL='http://localhost:8080'
export CLIENT_ID='management-demo'
export CLIENT_SECRET='management-demo-secret'
export RESOURCE_ID='management-service'
export RESOURCE_SECRET='management-resource-secret'
export ACCESS_TOKEN='<management-access-token>'
export REGISTRATION_ID='<registration-uuid>'
export ACTIVATION_ID='<existing-activation-uuid>'
```

| Endpoint group | Authentication | Requirement |
| --- | --- | --- |
| Authentication, registration, activation | Public | OTP and lifecycle rules still apply. |
| Token exchange and refresh | HTTP Basic | OAuth client ID and secret. |
| Logout | Bearer | Active access token for the profile. |
| Internal endpoints | HTTP Basic | Resource ID and secret; email checks also require system permission. |

Client and resource credentials are separate. See [consumer-service](../consumer-service/README.md) and [management-service](../management-service/README.md) for resource API usage.

## API endpoints

| Resource | Method | Endpoint | Description |
| --- | --- | --- | --- |
| Authentication | POST | [`/api/auth`](#authenticate) | Validate credentials and issue a login ticket. |
| Token | POST | [`/oauth2/token`](#exchange-a-login-ticket) / [refresh](#refresh-tokens) | Exchange a ticket or refresh tokens. |
| Logout | POST | [`/api/logout`](#log-out-all-devices-for-the-profile) | Invalidate tokens and tickets for the authenticated profile. |
| Registration | POST | [`/api/registration`](#register-email) | Register email and send OTP. |
| Registration | POST | [`/api/registration/{id}/otp/request`](#request-invitation-otp) | Request the first OTP for a management invitation. |
| Registration | POST | [`/api/registration/{id}/otp/validate`](#validate-registration-otp) | Validate OTP. |
| Registration | POST | [`/api/registration/{id}/otp/resend`](#resend-registration-otp) | Resend OTP. |
| Registration | POST | [`/api/registration/{id}/initialize`](#initialize-consumer-password) | Initialize the Consumer password. |
| Activation | POST | [`/api/activate/{id}`](#request-activation-otp) | Request activation OTP. |
| Activation | POST | [`/api/activate/{id}/otp/validate`](#validate-activation-otp) | Validate activation OTP. |
| Activation | POST | [`/api/activate/{id}/otp/resend`](#resend-activation-otp) | Resend activation OTP. |
| Activation | POST | [`/api/activate/{id}/initialize`](#initialize-management-password) | Initialize the Management password. |
| Internal Token | POST | [`/api/internal/tokens/introspect`](#introspect-an-access-token) | Validate token with live profile state. |
| Internal Profile | POST | [`/api/internal/profiles/email/check`](#check-email-duplication-within-a-system) | Check profile email duplication within a system. |
| Internal PIN Lock | POST | [`/api/internal/pin-locks/unlock`](#internal-permanent-pin-unlock) | Release a permanent PIN lock for an authorized system. |

### Authentication and tokens

#### Authenticate

`POST /api/auth`

[Back to API endpoints](#api-endpoints)

Requires an Active profile compatible with the client’s configured system and role. Returns a one-time, client-bound login ticket; this is not an OpenID Connect ID token. Response caching is disabled.

##### Request Form — `AuthenticationForm`

| Field | Type | Location | Required | Sample Value | Definition |
| --- | --- | --- | --- | --- | --- |
| `clientId` | `String` | JSON | Yes | `management-demo` | Nonblank registered OAuth client ID. |
| `username` | `String` | JSON | Yes | `admin@example.com` | Nonblank identity username; currently email. |
| `password` | `String` | JSON | Yes | `password` | Nonblank password for the profile’s system. |

```bash
curl -X POST "${BASE_URL}/api/auth" \
  -H 'Content-Type: application/json' \
  -d '{"clientId":"management-demo","username":"admin@example.com","password":"password"}'
```

##### Response DTO — `AuthenticationResult`

| Field | Type | Sample Value | Definition |
| --- | --- | --- | --- |
| `loginTicket` | `String` | `<one-time-ticket>` | Client-bound credential, consumed once. |
| `expiresIn` | `long` | `60` | Ticket lifetime in seconds. |

Response: `200 OK`, `application/json`.

```json
{
  "loginTicket": "<one-time-ticket>",
  "expiresIn": 60
}
```

#### Exchange a login ticket

`POST /oauth2/token`

[Back to API endpoints](#api-endpoints)

The ticket must be unexpired, unused, and issued for the authenticated client.

##### Request Form — OAuth Token Parameters

| Field | Type | Location | Required | Sample Value | Definition |
| --- | --- | --- | --- | --- | --- |
| `grant_type` | `String` | Form | Yes | `urn:jdc:params:oauth:grant-type:login-ticket` | Selects the grant. |
| `login_ticket` | `String` | Form | Yes | `<one-time-ticket>` | Credential returned by the preceding authentication or token request. |
| HTTP Basic username | `String` | Header | Yes | `management-demo` | Registered OAuth client ID. |
| HTTP Basic password | `String` | Header | Yes | `management-demo-secret` | Client secret; keep in a trusted backend. |

```bash
curl -X POST "${BASE_URL}/oauth2/token" \
  -u "${CLIENT_ID}:${CLIENT_SECRET}" \
  --data-urlencode 'grant_type=urn:jdc:params:oauth:grant-type:login-ticket' \
  --data-urlencode 'login_ticket=<one-time-ticket>'
```

##### Response DTO — OAuth Token Response

| Field | Type | Sample Value | Definition |
| --- | --- | --- | --- |
| `access_token` | `String` | `<access-token-jwt>` | JWT access token. |
| `token_type` | `String` | `Bearer` | Authorization scheme. |
| `expires_in` | `long` | `599` | Remaining access lifetime in seconds; may be slightly below 600. |
| `refresh_token` | `String` | `<refresh-token>` | Opaque refresh credential; default lifetime 8 hours. |
| `scope` | `String` | `management.api` | Space-separated granted scopes. |

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

#### Refresh tokens

`POST /oauth2/token`

[Back to API endpoints](#api-endpoints)

Refresh tokens rotate; store the latest returned token for the next refresh.

##### Request Form — OAuth Token Parameters

| Field | Type | Location | Required | Sample Value | Definition |
| --- | --- | --- | --- | --- | --- |
| `grant_type` | `String` | Form | Yes | `refresh_token` | Selects the grant. |
| `refresh_token` | `String` | Form | Yes | `<refresh-token>` | Credential returned by the preceding authentication or token request. |
| HTTP Basic username | `String` | Header | Yes | `management-demo` | Registered OAuth client ID. |
| HTTP Basic password | `String` | Header | Yes | `management-demo-secret` | Client secret; keep in a trusted backend. |

```bash
curl -X POST "${BASE_URL}/oauth2/token" \
  -u "${CLIENT_ID}:${CLIENT_SECRET}" \
  --data-urlencode 'grant_type=refresh_token' \
  --data-urlencode 'refresh_token=<refresh-token>'
```

##### Response DTO — OAuth Token Response

| Field | Type | Sample Value | Definition |
| --- | --- | --- | --- |
| `access_token` | `String` | `<access-token-jwt>` | JWT access token. |
| `token_type` | `String` | `Bearer` | Authorization scheme. |
| `expires_in` | `long` | `599` | Remaining access lifetime in seconds; may be slightly below 600. |
| `refresh_token` | `String` | `<refresh-token>` | Opaque refresh credential; default lifetime 8 hours. |
| `scope` | `String` | `management.api` | Space-separated granted scopes. |

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

Default clients: `consumer-demo` uses system Consumer and scope/audience `consumer.api`; `management-demo` uses system Management and scope/audience `management.api`. Access claims include profile UUID (`sub`), issuer, audience, system, roles, expiration, and token_version.

#### Log out all devices for the profile

`POST /api/logout`

[Back to API endpoints](#api-endpoints)

Increments the authenticated profile’s token version, invalidating its outstanding tickets, access tokens, and refresh tokens. Other profiles of the same identity are unaffected. Request body, path parameters, and query parameters: none.

##### Request Headers

| Field | Type | Location | Required | Sample Value | Definition |
| --- | --- | --- | --- | --- | --- |
| `Authorization` | `String` | Header | Yes | `Bearer <access-token>` | Active bearer token for the profile. |

```bash
curl -X POST "${BASE_URL}/api/logout" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

##### Response — Empty Body

| Field | Type | Sample Value | Definition |
| --- | --- | --- | --- |
| HTTP status | HTTP status | `200 OK` | Operation completed successfully. |
| Body | None | Empty | No response object is returned; do not parse JSON. |

```http
HTTP/1.1 200 OK

```

### Consumer registration

Self-registration creates a Pending Consumer profile after email verification and password initialization. Initialization returns a short-lived, one-time login ticket that can be exchanged for a restricted onboarding access token. That token can submit registration information to Consumer Service; it cannot call the ordinary Consumer API. Management approval activates the profile through the Consumer account approval event.

#### Register email

`POST /api/registration`

[Back to API endpoints](#api-endpoints)

##### Request Form — `RegistrationForm`

| Field | Type | Location | Required | Sample Value | Definition |
| --- | --- | --- | --- | --- | --- |
| `email` | `String` | JSON | Yes | `consumer@example.com` | Valid nonblank email receiving the verification code. |

```bash
curl -X POST "${BASE_URL}/api/registration" \
  -H 'Content-Type: application/json' \
  -d '{"email":"consumer@example.com"}'
```

##### Response DTO — `RegistrationResult`

| Field | Type | Sample Value | Definition |
| --- | --- | --- | --- |
| `key` | `UUID` | `550e8400-e29b-41d4-a716-446655440000` | Registration UUID used for subsequent calls. |

Response: `200 OK`, `application/json`.

```json
{
  "key": "550e8400-e29b-41d4-a716-446655440000"
}
```

#### Validate registration OTP

`POST /api/registration/{id}/otp/validate`

[Back to API endpoints](#api-endpoints)

##### Path Parameters

| Field | Type | Location | Required | Sample Value | Definition |
| --- | --- | --- | --- | --- | --- |
| `id` | `UUID` | Path | Yes | `550e8400-e29b-41d4-a716-446655440000` | registration record UUID; independent of the profile ID. |

##### Request Form — `OtpForm`

| Field | Type | Location | Required | Sample Value | Definition |
| --- | --- | --- | --- | --- | --- |
| `otpCode` | `String` | JSON | Yes | `123456` | Exactly six digits; use a string to preserve leading zeros. |

```bash
curl -X POST "${BASE_URL}/api/registration/${REGISTRATION_ID}/otp/validate" \
  -H 'Content-Type: application/json' \
  -d '{"otpCode":"123456"}'
```

##### Response — Empty Body

| Field | Type | Sample Value | Definition |
| --- | --- | --- | --- |
| HTTP status | HTTP status | `200 OK` | Operation completed successfully. |
| Body | None | Empty | No response object is returned; do not parse JSON. |

```http
HTTP/1.1 200 OK

```

#### Resend registration OTP

`POST /api/registration/{id}/otp/resend`

[Back to API endpoints](#api-endpoints)

##### Path Parameters

| Field | Type | Location | Required | Sample Value | Definition |
| --- | --- | --- | --- | --- | --- |
| `id` | `UUID` | Path | Yes | `550e8400-e29b-41d4-a716-446655440000` | registration record UUID; independent of the profile ID. |

Request body: none.

Replaces the previous code and expiration. Resend interval and failed-attempt cooldown limits apply.

```bash
curl -X POST "${BASE_URL}/api/registration/${REGISTRATION_ID}/otp/resend"
```

##### Response — Empty Body

| Field | Type | Sample Value | Definition |
| --- | --- | --- | --- |
| HTTP status | HTTP status | `200 OK` | Operation completed successfully. |
| Body | None | Empty | No response object is returned; do not parse JSON. |

```http
HTTP/1.1 200 OK

```

#### Request invitation OTP

Management-created Consumer accounts are already Approved in Consumer Service before Auth Server creates an invitation. The invitation email links to the configured `app.auth.consumer-invitation-url` route with the registration UUID. The client page should call the following endpoint to send the first OTP; subsequent requests are subject to the resend interval and cooldown rules.

`POST /api/registration/{id}/otp/request`

[Back to API endpoints](#api-endpoints)

Request body: none. The endpoint only accepts an unexpired, unused management invitation. Invitations expire after `app.auth.consumer-invitation-lifetime` (7 days by default). OTP validation and initialization use the same endpoints described above. Management invitation initialization creates an Active profile and therefore returns no onboarding ticket.

#### Initialize Consumer password

`POST /api/registration/{id}/initialize`

[Back to API endpoints](#api-endpoints)

##### Path Parameters

| Field | Type | Location | Required | Sample Value | Definition |
| --- | --- | --- | --- | --- | --- |
| `id` | `UUID` | Path | Yes | `550e8400-e29b-41d4-a716-446655440000` | registration record UUID; independent of the profile ID. |

##### Request Form — `InitializationForm`

| Field | Type | Location | Required | Sample Value | Definition |
| --- | --- | --- | --- | --- | --- |
| `password` | `String` | JSON | Yes | `your-password` | Nonblank initial password; requires successful OTP verification. |

```bash
curl -X POST "${BASE_URL}/api/registration/${REGISTRATION_ID}/initialize" \
  -H 'Content-Type: application/json' \
  -d '{"password":"your-password"}'
```

##### Response DTO — `InitializationResult`

| Field | Type | Sample Value | Definition |
| --- | --- | --- | --- |
| `id` | `UUID` | `7b16b09f-a949-4b74-a7e3-87b9c7d32202` | Created auth-profile UUID; distinct from registration/activation and subsystem account IDs. |
| `loginTicket` | `String (nullable)` | `eyJ...` | One-time ticket for a self-registration Pending profile; null for an approved management invitation. |
| `expiresIn` | `long` | `60` | Ticket lifetime in seconds; `0` when there is no ticket. |

Response: `200 OK`, `application/json`.

```json
{
  "id": "7b16b09f-a949-4b74-a7e3-87b9c7d32202",
  "loginTicket": "one-time-ticket-value",
  "expiresIn": 60
}
```

The self-registration client exchanges `loginTicket` using the configured `consumer-demo` client and the custom login-ticket grant, then sends the returned access token to `POST /api/mobile/registration`. The onboarding token has only the `consumer.onboarding` scope, has no refresh token, and cannot be used after Consumer approval changes the profile to Active. The mobile flow must obtain a normal login token after approval.

### Management activation

Management Service creates a local Pending account and stores a versioned invitation event in its outbox in the same transaction. Auth Server consumes that event, creates an activation record keyed by the management account UUID, and queues an activation email in its own email outbox. Invitations expire after `app.auth.management-activation-lifetime` (7 days by default). After OTP verification and password initialization, Auth Server creates an Active Management profile and stores an activation-completed event in its Kafka outbox. Management Service consumes that event and changes its local account to Activated. Both event consumers handle duplicate delivery idempotently.

#### Request activation OTP

`POST /api/activate/{id}`

[Back to API endpoints](#api-endpoints)

##### Path Parameters

| Field | Type | Location | Required | Sample Value | Definition |
| --- | --- | --- | --- | --- | --- |
| `id` | `UUID` | Path | Yes | `550e8400-e29b-41d4-a716-446655440000` | Management account UUID used as the activation record ID. |

Request body: none.

```bash
curl -X POST "${BASE_URL}/api/activate/${ACTIVATION_ID}"
```

##### Response — Empty Body

| Field | Type | Sample Value | Definition |
| --- | --- | --- | --- |
| HTTP status | HTTP status | `200 OK` | Operation completed successfully. |
| Body | None | Empty | No response object is returned; do not parse JSON. |

```http
HTTP/1.1 200 OK

```

#### Validate activation OTP

`POST /api/activate/{id}/otp/validate`

[Back to API endpoints](#api-endpoints)

##### Path Parameters

| Field | Type | Location | Required | Sample Value | Definition |
| --- | --- | --- | --- | --- | --- |
| `id` | `UUID` | Path | Yes | `550e8400-e29b-41d4-a716-446655440000` | Management account UUID used as the activation record ID. |

##### Request Form — `OtpForm`

| Field | Type | Location | Required | Sample Value | Definition |
| --- | --- | --- | --- | --- | --- |
| `otpCode` | `String` | JSON | Yes | `123456` | Exactly six digits; use a string to preserve leading zeros. |

```bash
curl -X POST "${BASE_URL}/api/activate/${ACTIVATION_ID}/otp/validate" \
  -H 'Content-Type: application/json' \
  -d '{"otpCode":"123456"}'
```

##### Response — Empty Body

| Field | Type | Sample Value | Definition |
| --- | --- | --- | --- |
| HTTP status | HTTP status | `200 OK` | Operation completed successfully. |
| Body | None | Empty | No response object is returned; do not parse JSON. |

```http
HTTP/1.1 200 OK

```

#### Resend activation OTP

`POST /api/activate/{id}/otp/resend`

[Back to API endpoints](#api-endpoints)

##### Path Parameters

| Field | Type | Location | Required | Sample Value | Definition |
| --- | --- | --- | --- | --- | --- |
| `id` | `UUID` | Path | Yes | `550e8400-e29b-41d4-a716-446655440000` | Management account UUID used as the activation record ID. |

Request body: none.

Replaces the previous code and expiration. Resend interval and failed-attempt cooldown limits apply.

```bash
curl -X POST "${BASE_URL}/api/activate/${ACTIVATION_ID}/otp/resend"
```

##### Response — Empty Body

| Field | Type | Sample Value | Definition |
| --- | --- | --- | --- |
| HTTP status | HTTP status | `200 OK` | Operation completed successfully. |
| Body | None | Empty | No response object is returned; do not parse JSON. |

```http
HTTP/1.1 200 OK

```

#### Initialize Management password

`POST /api/activate/{id}/initialize`

[Back to API endpoints](#api-endpoints)

##### Path Parameters

| Field | Type | Location | Required | Sample Value | Definition |
| --- | --- | --- | --- | --- | --- |
| `id` | `UUID` | Path | Yes | `550e8400-e29b-41d4-a716-446655440000` | Management account UUID used as the activation record ID. |

##### Request Form — `InitializationForm`

| Field | Type | Location | Required | Sample Value | Definition |
| --- | --- | --- | --- | --- | --- |
| `password` | `String` | JSON | Yes | `your-password` | Nonblank initial password; requires successful OTP verification. |

```bash
curl -X POST "${BASE_URL}/api/activate/${ACTIVATION_ID}/initialize" \
  -H 'Content-Type: application/json' \
  -d '{"password":"your-password"}'
```

##### Response DTO — `InitializationResult`

| Field | Type | Sample Value | Definition |
| --- | --- | --- | --- |
| `id` | `UUID` | `7b16b09f-a949-4b74-a7e3-87b9c7d32202` | Created auth-profile UUID; distinct from registration/activation and subsystem account IDs. |

Response: `200 OK`, `application/json`.

```json
{
  "id": "7b16b09f-a949-4b74-a7e3-87b9c7d32202"
}
```

### Internal token validation

#### Introspect an access token

`POST /api/internal/tokens/introspect`

[Back to API endpoints](#api-endpoints)

Checks persisted authorization, token expiration/revocation, profile state, token version, and resource audience permission on every request. Uses resource credentials, not client credentials or user bearer authentication. No response caching.

##### Request Form — Introspection Parameters

| Field | Type | Location | Required | Sample Value | Definition |
| --- | --- | --- | --- | --- | --- |
| `token` | `String` | Form | Yes | `<access-token>` | Access token to validate. |
| HTTP Basic username | `String` | Header | Yes | `management-service` | Configured resource ID. |
| HTTP Basic password | `String` | Header | Yes | `management-resource-secret` | Resource secret. |

```bash
curl -X POST "${BASE_URL}/api/internal/tokens/introspect" \
  -u "${RESOURCE_ID}:${RESOURCE_SECRET}" \
  --data-urlencode 'token=<access-token>'
```

##### Response DTO — `Map<String, Object>`

| Field | Type | Sample Value | Definition |
| --- | --- | --- | --- |
| `active` | boolean | `true` | Whether the token is accepted. |
| `sub` | String | `<profile-uuid>` | Auth-profile UUID. |
| `username` | String | `admin@example.com` | Authoritative identity username for downstream account lookup. |
| `iss` | String | `http://localhost:8080` | Issuer. |
| `aud` | List<String> | `["management.api"]` | Token audiences. |
| `scope` | String | `management.api` | Space-separated scopes. |
| `system` | String | `Management` | Profile system. |
| `roles` | List<String> | `["Administrator"]` | Current profile role. |
| `token_version` | long | `0` | Profile token version at issuance. |
| `iat / exp` | long | `1795170600 / 1795171200` | Issued-at and expiry Unix seconds. |

Response: `200 OK`, `application/json`; selected attributes shown. Attributes other than active are present for valid tokens.

```json
{
  "active": true,
  "sub": "7b16b09f-a949-4b74-a7e3-87b9c7d32202",
  "username": "admin@example.com",
  "iss": "http://localhost:8080",
  "aud": [
    "management.api"
  ],
  "scope": "management.api",
  "system": "Management",
  "roles": [
    "Administrator"
  ],
  "token_version": 0,
  "iat": 1795170600,
  "exp": 1795171200
}
```

Invalid, expired, revoked, or disallowed-audience tokens return `200 OK`:

```json
{
  "active": false
}
```

### Internal profile email check

#### Check email duplication within a system

`POST /api/internal/profiles/email/check`

[Back to API endpoints](#api-endpoints)

Requires resource credentials and explicit `app.auth.resources.<resource-id>.email-check-systems` permission. Defaults grant Consumer to consumer-service, and Consumer/Management to management-service. New resources have no email-check permission until configured. The requested system must exist in `app.auth.systems`.

##### Request Form — `EmailCheckForm`

| Field | Type | Location | Required | Sample Value | Definition |
| --- | --- | --- | --- | --- | --- |
| `system` | `String` | JSON | Yes | `Management` | Nonblank case-sensitive system, maximum 255 characters. |
| `email` | `String` | JSON | Yes | `alice@example.com` | Valid nonblank email, maximum 255 characters; matched exactly. |
| HTTP Basic username | `String` | Header | Yes | `management-service` | Configured resource ID. |
| HTTP Basic password | `String` | Header | Yes | `management-resource-secret` | Resource secret. |

```bash
curl -X POST "${BASE_URL}/api/internal/profiles/email/check" \
  -u "${RESOURCE_ID}:${RESOURCE_SECRET}" \
  -H 'Content-Type: application/json' \
  -d '{"system":"Management","email":"alice@example.com"}'
```

##### Response DTO — `EmailCheckResult`

| Field | Type | Sample Value | Definition |
| --- | --- | --- | --- |
| `identityExists` | `boolean` | `true` | An identity exists for the email address, including identities used by another system. |
| `profileExists` | `boolean` | `true` | A profile exists for this email address in the requested system, regardless of lifecycle state. |
| `profileStatus` | `String (nullable)` | `Active` | Status of the matching profile; null when the requested system has no profile. |

Response: `200 OK`, `application/json`, with `Cache-Control: no-store`.

```json
{
  "identityExists": true,
  "profileExists": true,
  "profileStatus": "Active"
}
```

No matching profile returns:

```json
{
  "identityExists": false,
  "profileExists": false,
  "profileStatus": null
}
```

Pending, Active, Locked, and Closed profiles count as existing. An identity used only in another system, or registration/activation records without a profile, does not count. This check does not reserve an email; final initialization must enforce uniqueness. Management Service and Consumer Service call it before account creation; the final profile constraint enforces uniqueness across concurrent requests. Consumer approval and rejection events are consumed from versioned Kafka topics. Management-created Consumer invitations are written to an email outbox and retried until the mail send succeeds.

### Internal permanent PIN unlock

`POST /api/internal/pin-locks/unlock` requires HTTP Basic resource credentials. The resource must list the requested system under `app.auth.resources.<resource-id>.pin-lock-systems`.

[Back to API endpoints](#api-endpoints)

| Field | Type | Required | Sample | Definition |
| --- | --- | --- | --- | --- |
| `system` | `String` | Yes | `Consumer` | Configured profile system to unlock. |
| `username` | `String` | Yes | `alice@example.com` | Identity username whose permanent PIN lock should be released. |
| `requestedBy` | `String` | Yes | `<management-profile-id>` | Management actor recorded in lock history. |

```bash
curl -X POST "${BASE_URL}/api/internal/pin-locks/unlock" \
  -u "${RESOURCE_ID}:${RESOURCE_SECRET}" \
  -H 'Content-Type: application/json' \
  -d '{"system":"Consumer","username":"alice@example.com","requestedBy":"<management-profile-id>"}'
```

Success returns `200 OK` with an empty body. The operation changes Auth Server state and publishes a PIN lock activity event for the resource services' history projections.

## Error responses

Registration, activation, and authentication endpoints allow unauthenticated requests. Logout requires a bearer access token. Internal introspection, email checks, and PIN unlock require resource-server credentials. Email checks and PIN unlock additionally enforce explicit per-resource system permissions. `/oauth2/token` requires OAuth client authentication using HTTP Basic.

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

### Response DTO — `ProblemDetail`

| Field | Type | Sample Value | Definition |
| --- | --- | --- | --- |
| `type` | URI | `about:blank` | Problem type identifier. |
| `title` | String | `Bad Request` | HTTP error title. |
| `status` | int | `400` | HTTP status. |
| `detail` | String | `Please correct the invalid request fields.` | Explanation. |
| `instance` | URI | `/api/auth` | Request path. |
| `errors` | List<FieldError> (optional) | `[]` | Present for request-field validation failures. |

### List Item — `FieldError`

| Field | Type | Sample Value | Definition |
| --- | --- | --- | --- |
| `field` | String | `password` | Invalid field name. |
| `message` | String | `Enter password` | Validation message. |

### Response DTO — OAuth Error

| Field | Type | Sample Value | Definition |
| --- | --- | --- | --- |
| `error` | String | `invalid_grant` | OAuth error code. |
| `error_description` | String (optional) | `Invalid grant` | Additional error explanation when provided. |
| `error_uri` | URI (optional) | Not always present | Reference information when provided. |

## Implementation notes

### Identity and administrator

An identity stores the username. A profile stores the password, system, role, status, and token version. An identity can have one profile per system, allowing separate passwords for Consumer and Management.

| Profile | System | Role | Initial status |
| --- | --- | --- | --- |
| Consumer registration | Consumer | Consumer | Pending |
| Management activation | Management | Management | Active |
| Bootstrap administrator | Management | Administrator | Active |

At startup, `AdministratorInitializer` creates an administrator only when there are no profiles. The administrator does not require OTP activation. Changing administrator properties later does not reset an existing account.

### Persistent clients and authorizations

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
| `status` | `Active` | Normal authentication requires Active. Pending self-registration profiles can receive only the restricted, short-lived onboarding ticket until approval; Locked and Closed profiles cannot authenticate. |

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

For example, changing `activation.system` to `Merchant` and `activation.role` to `MerchantOperator` makes the existing activation workflow create Active Merchant profiles. It still requires an activation record. The built-in Kafka invitation listener creates records only for the Management account invitation contract; adding another system's invitation event requires a corresponding producer and listener. Registration creates Pending profiles. Administrator settings apply only when bootstrapping an empty system.

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

app:
  auth:
    introspection-uri: http://localhost:8080/api/internal/tokens/introspect
    issuer: http://localhost:8080
    resource-id: merchant-service
    resource-secret: ${MERCHANT_RESOURCE_SECRET}
    audiences: [merchant.api]
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

## Tests

From the project root:

```bash
# Authorization server tests only
mvn -pl auth-server clean test

# All modules
mvn clean test
```

The tests use mocks, test security configurations, and embedded H2 for JDBC token persistence and schema validation; they do not require running PostgreSQL, SMTP, or Kafka. They do not replace integration testing against the Docker services.

## Current boundaries

- Client registrations and OAuth authorizations use shared PostgreSQL storage. Non-production startup creates a persistent RSA PEM key pair under `~/.wallet-demo/`; production requires provisioned shared key files. Key rotation remains an operational process. Current internal introspection validates persisted token records rather than JWT signatures.
- Auth Server consumes versioned PIN lock setting events from Management Service and stores the latest snapshot in `auth.pin_lock_setting`. Management Service uses a transactional outbox and a compacted topic. `PinLockActivityService` tracks PIN failures, applies temporary/permanent thresholds, releases temporary locks lazily on the next PIN operation, and publishes lock or permanent-unlock transitions through the auth outbox. Consumer and Management Services consume those transitions and keep idempotent local history. Consumer account approvals and rejections also use transactional outboxes. Auth Server activates or closes Pending self-registration profiles and creates invitation registrations for management-created accounts. Invitation email delivery uses a retryable Auth Server outbox. The current PIN lock setting has no separate counting-window fields. The existing username/password login is separate; a PIN verifier must call `checkBeforePinAttempt`, `recordFailedAttempt`, and `recordSuccessfulAttempt` around its own PIN check.
- Public authentication, registration, and activation routes permit only their documented POST endpoints. Other routes are denied; logout and internal resource APIs have dedicated security chains.
- Password initialization and first administrator bootstrap require at least 12 characters and reject passwords exceeding BCrypt's 72-byte UTF-8 limit.

Further examples: [authentication](../readme/authentication-endpoint.md), [token endpoint](../readme/token-endpoint.md), [logout](../readme/logout.md), and [administrator bootstrap](../readme/administrator-bootstrap.md).
