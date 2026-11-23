# Management Service

Manages management accounts, PIN lock policies, and configured client systems. Default base URL: `http://localhost:8082`.

## Contents

- [Run locally](#run-locally)
- [Configuration](#configuration)
- [Database information](#database-information)
- [Request conventions](#request-conventions)
- [Management Endpoints](#management-endpoints)
- [Error responses](#error-responses)
- [Implementation notes](#implementation-notes)
- [Tests](#tests)
- [Current boundaries](#current-boundaries)

## Run locally

Requires Java 25, Maven, and Docker Compose. From the project root:

```bash
export POSTGRES_PASSWORD=postgres
docker compose up -d
mvn -pl management-service spring-boot:run
```

Default base URL: `http://localhost:8082`. Auth-server must be running for authenticated API requests and account email-availability checks. See [auth-server](../auth-server/README.md) for obtaining tokens. Set `SERVER_PORT` separately for each service process.

PostgreSQL initializes `postgres/01-init.sql` and `postgres/04-management-schema.sql` only when its database volume is empty. Editing SQL does not modify an existing database. Hibernate uses `ddl-auto: validate`; no migration or automatic table creation/update is enabled. During this planning phase, update the initialization SQL directly. If you intentionally recreate the development database, `docker compose down -v` removes all Compose volumes, including database and Kafka data.

Build and run an executable JAR:

```bash
mvn -pl management-service clean package
java -jar management-service/target/management-service-0.0.1-SNAPSHOT.jar
```

Applications run on the host by default; infrastructure runs in Docker. Container deployments need connection-address overrides. [Consumer service](../consumer-service/README.md) uses port 8081, [Management service](../management-service/README.md) uses 8082, and auth-server uses 8080.

## Configuration

Defaults are in [`application.yml`](src/main/resources/application.yml).

| Environment variable | Default | Purpose |
| --- | --- | --- |
| `SERVER_PORT` | 8082 | HTTP port. |
| `MANAGEMENT_DB_URL` | `jdbc:postgresql://localhost:5432/wallet_demo?currentSchema=management` | JDBC URL. |
| `MANAGEMENT_DB_USERNAME` | `mnguser` | Service database role. |
| `MANAGEMENT_DB_PASSWORD` | `mngpass` | Development database password. |
| `AUTH_INTROSPECTION_URI` | `http://localhost:8080/api/internal/tokens/introspect` | Live token-validation endpoint. |
| `AUTH_ISSUER` | `http://localhost:8080` | Expected issuer. |
| `MANAGEMENT_RESOURCE_ID` | `management-service` | Resource ID registered in auth-server. |
| `MANAGEMENT_RESOURCE_SECRET` | `management-resource-secret` | Resource credential; must match auth-server. |
| `AUTH_AUDIENCES` | `management.api` | Accepted audiences; comma-separated list. |
| `AUTH_CONNECT_TIMEOUT` / `AUTH_READ_TIMEOUT` | `3s` / `3s` | Positive HTTP timeouts. |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker. |
| `KAFKA_CONSUMER_GROUP_ID` | `management-service` | Kafka consumer group. |
| `PIN_LOCK_SETTING_TOPIC` | `pin-lock-setting-changed.v1` | Topic for setting-change events. |
| `PIN_LOCK_SETTING_TOPIC_PARTITIONS` | `3` | Topic partition count. |
| `PIN_LOCK_SETTING_TOPIC_REPLICAS` | `1` | Topic replication factor; increase for a multi-broker deployment. |
| `KAFKA_OUTBOX_FIXED_DELAY` | `1000` | Delay in milliseconds between outbox polling cycles. |
| `PIN_LOCK_ACTIVITY_TOPIC` | `pin-lock-activity-changed.v1` | PIN lock activity topic. |
| `PIN_LOCK_ACTIVITY_CONSUMER_GROUP` | `management-service-pin-lock-history` | Management Service's independent history consumer group. |
| `AUTH_PIN_LOCK_UNLOCK_URI` | `http://localhost:8080/api/internal/pin-locks/unlock` | Auth Server's internal permanent-unlock endpoint. |
| `AUTH_EMAIL_CHECK_URI` | `http://localhost:8080/api/internal/profiles/email/check` | Management email availability endpoint. |

Custom security settings are bound to validated `AuthenticationProps` under `app.auth`:

```yaml
app:
  auth:
    introspection-uri: ${AUTH_INTROSPECTION_URI:http://localhost:8080/api/internal/tokens/introspect}
    email-check-uri: ${AUTH_EMAIL_CHECK_URI:http://localhost:8080/api/internal/profiles/email/check}
    issuer: ${AUTH_ISSUER:http://localhost:8080}
    resource-id: management-service
    resource-secret: ${MANAGEMENT_RESOURCE_SECRET:management-resource-secret}
    audiences: [management.api]
    connect-timeout: ${AUTH_CONNECT_TIMEOUT:3s}
    read-timeout: ${AUTH_READ_TIMEOUT:3s}
```

Override the audience list with `AUTH_AUDIENCES` (comma-separated). Empty credentials/audiences and non-positive HTTP timeouts fail configuration validation. Changes require a restart.

| Configuration | Owner |
| --- | --- |
| Security filter chain and introspection HTTP client | `ResourceSecurityConfig`, using `AuthenticationProps` |
| Custom repository base class and method validation | `RepositoryConfig` and `ValidatedRepositoryFactoryBean` |
| DataSource, EntityManagerFactory, transaction manager | Spring Boot auto-configuration from `spring.datasource` / `spring.jpa` |
| KafkaTemplate, producer/consumer factories, listener container factory | Spring Boot auto-configuration from `spring.kafka` |
| Topic settings and outbox polling interval | `KafkaProps`, bound from `app.kafka` and validated at startup |
| Compacted PIN setting topic declaration | `KafkaTopicConfig` |
| PIN lock activity event listener | `PinLockActivityEventListener`, with its own consumer group |

The PostgreSQL defaults use `wallet_demo`, schema `management`, and role `mnguser` with development password `mngpass`. Override with `MANAGEMENT_DB_URL`, `MANAGEMENT_DB_USERNAME`, and `MANAGEMENT_DB_PASSWORD`. Hibernate uses `ddl-auto: validate` and `open-in-view: false`; tables are created by the scripts under `postgres`. Docker init scripts run only for a new database volume, so recreate the local volume after schema changes during development.

Kafka defaults to `localhost:9092`, String keys/values (including JSON text), record acknowledgment, and idempotent producer settings. `KafkaProps` binds topic settings, the management history listener group, and outbox polling interval from `app.kafka`. Management Service creates the compacted PIN setting topic and publishes setting events through its outbox. It also consumes PIN lock activity with its own group and records Management-system history idempotently. Permanent unlock requests go to Auth Server; temporary locks expire by `lockedUntil` when the next PIN operation is checked, without a scheduler.

Host defaults assume services run on the host and infrastructure runs in Docker Compose. For container deployments, override localhost connection addresses appropriately.

### Account email availability check

`AuthenticationProps.emailCheckUri` uses `app.auth.email-check-uri`, overridden by `AUTH_EMAIL_CHECK_URI`; default `http://localhost:8080/api/internal/profiles/email/check`. The HTTP client uses the configured resource credentials and timeouts. Auth-server must authorize management-service for `email-check-systems: [Management]` (the development defaults already include Management). Errors or invalid responses fail closed with 503.

### Client systems

`ClientSystemProps` binds the configured client-system list under `app.client`:

```yaml
app:
  client:
    systems:
      - Consumer
      - Management
```

To add a system, append its case-sensitive name and restart management-service. It appears in the full system list and, until settings exist, the available-systems list. Create its policy with `POST /api/setting/pin-locks/{system}`. This list does not register OAuth clients or configure auth-server systems; those require their own auth-server configuration.

`POSTGRES_PASSWORD` configures the container superuser, not the service role password in `01-init.sql`. Keep SQL credentials and application settings consistent. Development credentials are examples; replace them for deployment. This service does not send email directly; auth-server owns SMTP and OTP delivery.

## Database information

### Connection and ownership

| Setting | Local development value | Definition |
| --- | --- | --- |
| Engine | PostgreSQL 17 | Docker Compose database. |
| Host and port | `localhost:5432` | Host application connection. |
| Database | `wallet_demo` | Shared development database with service-specific schemas. |
| Schema | `management` | Owned exclusively by this service. |
| Database role | `mnguser` | Role owns the schema and tables. |
| Password | `mngpass` | Development password from 01-init.sql. |
| JDBC URL | `jdbc:postgresql://localhost:5432/wallet_demo?currentSchema=management` | Selects the default schema. |
| Hibernate DDL mode | `validate` | Checks mappings without changing tables. |

The role search path and JDBC currentSchema select `management`; Hibernate also uses `hibernate.default_schema=management`. Cross-service access uses APIs and future events, rather than direct reads of other services’ tables. Subsystem account UUIDs are independent of auth-profile UUIDs.

### Tables

| Table | Primary key | Purpose |
| --- | --- | --- |
| `account` | `id` — UUID | Management name, phone, unique email, status, registration and activation instants. |
| `pin_lock_history` | `id` — UUID | PIN lock and release activity received from Auth Server. |
| `pin_lock_setting` | `system` — VARCHAR(255) | One policy per system with temporary/permanent thresholds, duration, deleted flag, and optimistic version. |
| `outbox_event` | `id` — UUID | Durable event payloads waiting to be published to Kafka, plus publish/retry status. |

### Table definitions

Column names and PostgreSQL types below follow the initialization SQL. **Nullable** describes database nullability. **Default** is the SQL default, not a Java initializer or service-assigned value; `—` means no explicit SQL default. PK = primary key, FK = foreign key. Primary-key columns are implicitly NOT NULL.

#### `management.account`

| Column | PostgreSQL Type | Nullable | Default | Key / Constraints | Definition |
| --- | --- | --- | --- | --- | --- |
| `id` | `UUID` | No | — | PK | Record UUID generated by Hibernate; no database UUID default. |
| `name` | `VARCHAR(255)` | No | — | — | Management account name. |
| `phone` | `VARCHAR(255)` | No | — | — | Account phone number. |
| `email` | `VARCHAR(255)` | No | — | UNIQUE | Email associated with this record. |
| `status` | `SMALLINT` | No | — | CHECK (status BETWEEN 0 AND 1) | Account state: 0 Pending, 1 Activated. |
| `registered_at` | `TIMESTAMP(6) WITH TIME ZONE` | No | — | — | Registration instant. |
| `activated_at` | `TIMESTAMP(6) WITH TIME ZONE` | Yes | — | — | Activation instant; null until activated. |

Table constraints and indexes:

| Name | Definition |
| --- | --- |
| `ck_management_account_status` | `CHECK (status BETWEEN 0 AND 1)` |

#### `management.pin_lock_history`

| Column | PostgreSQL Type | Nullable | Default | Key / Constraints | Definition |
| --- | --- | --- | --- | --- | --- |
| `id` | `UUID` | No | — | PK | Local history record UUID. |
| `event_id` | `UUID` | No | — | UNIQUE | Auth Server event ID used to ignore duplicate Kafka delivery. |
| `account_id` | `UUID` | No | — | FK → management.account.id | Management account whose PIN state changed. |
| `lock_type` | `SMALLINT` | No | — | CHECK (lock_type BETWEEN 0 AND 2) | Activity type: 0 Temporary, 1 Permenant, 2 Released. |
| `lock_at` | `TIMESTAMP(6) WITH TIME ZONE` | No | — | — | Lock or release event instant. |
| `locked_until` | `TIMESTAMP(6) WITH TIME ZONE` | Yes | — | — | Expiry instant for a temporary lock. |
| `requested_by` | `VARCHAR(255)` | Yes | — | — | Management principal that requested a permanent unlock. |

#### `management.pin_lock_setting`

| Column | PostgreSQL Type | Nullable | Default | Key / Constraints | Definition |
| --- | --- | --- | --- | --- | --- |
| `system` | `VARCHAR(255)` | No | — | PK | Case-sensitive policy system; validated against app.client.systems by the service. |
| `temp_lock_fails_count` | `INTEGER` | No | — | — | Failed-PIN threshold for a temporary lock. |
| `temp_lock_wailt_minutes` | `INTEGER` | No | — | — | Temporary-lock duration in minutes; API field tempLockWaitMinutes. |
| `permenant_lock_fails_count` | `INTEGER` | No | — | — | Permanent-lock failure threshold; API field permanentLockFailsCount. |
| `deleted` | `BOOLEAN` | No | — | — | Soft-delete flag; deleted policies are excluded from list results. |
| `version` | `BIGINT` | No | `0` | — | JPA optimistic version used to order setting-change events. |

#### `management.outbox_event`

| Column | PostgreSQL Type | Nullable | Default | Key / Constraints | Definition |
| --- | --- | --- | --- | --- | --- |
| `id` | `UUID` | No | — | PK | Event ID, also included in the published payload. |
| `topic` | `VARCHAR(100)` | No | — | — | Kafka topic selected for this event. |
| `aggregate_key` | `VARCHAR(255)` | No | — | — | System name used as the Kafka record key. |
| `payload` | `TEXT` | No | — | — | JSON representation of the setting-change event. |
| `created_at` | `TIMESTAMP(6) WITH TIME ZONE` | No | — | — | When the outbox event was created. |
| `published_at` | `TIMESTAMP(6) WITH TIME ZONE` | Yes | — | — | When Kafka acknowledged publication; null means pending. |
| `attempt_count` | `INTEGER` | No | `0` | — | Number of recorded publish failures. |
| `last_error` | `TEXT` | Yes | — | — | Most recent publish error, if any. |

### Relationships and constraints

Account email is unique. Policy system is a string primary key checked against app.client.systems by the service when creating/updating settings. No database relationship connects local accounts to auth-server profiles.

### Stored identifiers and status values

| Column | Stored values |
| --- | --- |
| `account.status` | 0 = Pending; 1 = Activated. |
| `pin_lock_setting.system` | Case-sensitive configured string, such as Consumer. |
| `pin_lock_setting.deleted` | Boolean; deleted rows are excluded from reads. |

The API and event use corrected names tempLockWaitMinutes and permanentLockFailsCount. Existing entity/database spellings remain tempLockWailtMinutes / temp_lock_wailt_minutes and permenantLockFailsCount / permenant_lock_fails_count; the service and DTO map between them.

### PIN lock setting event publication

Creating or updating a setting stores a versioned `PinLockSettingChangedEvent` in `management.outbox_event` in the same database transaction as the setting. A scheduled publisher sends pending records to the compacted `pin-lock-setting-changed.v1` topic using the system as the Kafka key. Successful sends are marked published; failures remain pending and are retried. A crash after Kafka accepts an event but before the outbox row is marked can produce a duplicate, so consumers must be idempotent.

### Security data and timestamps

Passwords and OAuth tokens belong to auth-server and are not stored in these tables. Names, emails, phones, and other personal information are stored as ordinary columns in this demo. Event timestamps use Instant / TIMESTAMP WITH TIME ZONE. Status enums use ordinal SMALLINT values; keep enum order and SQL constraints aligned.

### Initialization and inspection

[`01-init.sql`](../postgres/01-init.sql) creates service roles and schemas; [`04-management-schema.sql`](../postgres/04-management-schema.sql) creates the tables. Initialization runs only for an empty PostgreSQL volume.

```bash
docker compose exec postgres psql -U mnguser -d wallet_demo
```

Inside psql:

```text
\dt management.*
\d management.account
```

## Request conventions

All documented endpoints require a bearer access token with the `management.api` scope. Tokens are validated through auth-server introspection on every request. Security rules currently enforce scopes rather than fine-grained management roles.

```bash
export BASE_URL='http://localhost:8082'
export MANAGEMENT_TOKEN='<management-access-token>'
```

Use `Content-Type: application/json` for JSON request bodies. System names are case-sensitive and must match `app.client.systems`, for example `Consumer`. An empty response is not `{}` or `null`. List endpoints return plain JSON arrays without pagination metadata.

## Management Endpoints

For authenticated management users maintaining system PIN lock policies.

| Resource | Method | Endpoint | Description |
| --- | --- | --- | --- |
| Account | GET | `/api/accounts` | Search management accounts. |
| Account | POST | `/api/accounts` | Create a Pending management account. |
| Account | PUT | `/api/accounts/{id}` | Update account name and phone. |
| Client System | GET | `/api/setting/systems` | List all configured client systems. |
| PIN Lock Setting | GET | `/api/setting/pin-locks` | List non-deleted PIN lock settings. |
| PIN Lock Setting | GET | `/api/setting/pin-locks/systems` | List configured systems available for creating settings. |
| PIN Lock Setting | POST | `/api/setting/pin-locks/{system}` | Create PIN lock settings for a configured system. |
| PIN Lock Setting | PUT | `/api/setting/pin-locks/{system}` | Update existing PIN lock settings. |

### Account

#### Search management accounts

`GET /api/accounts`

Returns accounts ordered by registeredAt descending, then UUID ascending. No request body. Date filters are inclusive UTC calendar dates.

##### Search Form — `AccountSearch`

| Field | Type | Location | Required | Sample Value | Definition |
| --- | --- | --- | --- | --- | --- |
| `status` | `Account.Status` | Query | No | `Pending` | Pending or Activated. |
| `createdFrom` | `LocalDate` | Query | No | `2026-11-01` | Inclusive registration date in UTC. |
| `createdTo` | `LocalDate` | Query | No | `2026-11-30` | Inclusive registration date; must not precede createdFrom. |
| `keyword` | `String` | Query | No | `alice` | Case-insensitive literal prefix of name, email, or phone. Wildcards are escaped. |

##### Pagination Parameters

| Field | Type | Location | Required | Sample Value | Definition |
| --- | --- | --- | --- | --- | --- |
| `page` | `int` | Query | No | `0` | Zero-based page index, default 0; must not be negative. |
| `size` | `int` | Query | No | `10` | Page size, default 10; must be positive. |

##### Response DTO — `Page<AccountListItem>`

| Field | Type | Sample Value | Definition |
| --- | --- | --- | --- |
| `content` | `List<AccountListItem>` | `[]` | Records on the requested page. |
| `number` | `int` | `0` | Zero-based page index. |
| `size` | `int` | `10` | Requested page size. |
| `totalElements` | `long` | `1` | Total matching accounts. |
| `totalPages` | `int` | `1` | Total matching pages. |

##### List Item — `AccountListItem`

| Field | Type | Sample Value | Definition |
| --- | --- | --- | --- |
| `id` | `UUID` | `550e8400-e29b-41d4-a716-446655440000` | Local management account UUID, independent of auth-profile UUID. |
| `name` | `String` | `Alice` | Account name. |
| `phone` | `String` | `09123456789` | Phone number. |
| `email` | `String` | `alice@example.com` | Account email. |
| `status` | `Account.Status` | `Pending` | Pending or Activated. |
| `registeredAt` | `Instant` | `2026-11-22T06:30:00Z` | Registration instant. |
| `activatedAt` | `Instant (nullable)` | `null` | Activation instant; null until activated. |

```bash
curl --get "${BASE_URL}/api/accounts" \
  -H "Authorization: Bearer ${MANAGEMENT_TOKEN}" \
  --data-urlencode 'status=Pending' \
  --data-urlencode 'createdFrom=2026-11-01' \
  --data-urlencode 'createdTo=2026-11-30' \
  --data-urlencode 'keyword=alice' \
  --data-urlencode 'page=0' \
  --data-urlencode 'size=10'
```

Response: `200 OK`, `application/json`. Principal page fields are shown; Spring Data may include additional pagination metadata.

```json
{
  "content": [{
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "Alice",
    "phone": "09123456789",
    "email": "alice@example.com",
    "status": "Pending",
    "registeredAt": "2026-11-22T06:30:00Z",
    "activatedAt": null
  }],
  "number": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1
}
```

#### Create a management account

`POST /api/accounts`

Checks local email duplication, then calls auth-server’s internal email-check endpoint with system=Management using resource credentials. Creates a Pending account and records registeredAt. The remote check is advisory; final auth-profile creation must enforce uniqueness again. Invitation publishing and activation consumption remain TODOs; this endpoint does not send verification email or create an auth profile.

##### Request Form — `AccountRegistrationForm`

| Field | Type | Location | Required | Sample Value | Definition |
| --- | --- | --- | --- | --- | --- |
| `name` | `String` | JSON | Yes | `Alice` | Nonblank name, maximum 255 characters. |
| `phone` | `String` | JSON | Yes | `09123456789` | Nonblank phone, maximum 255 characters. |
| `email` | `String` | JSON | Yes | `alice@example.com` | Valid nonblank email, maximum 255 characters; exact matching. Must remain unchanged on update. |

```json
{
  "name": "Alice",
  "phone": "09123456789",
  "email": "alice@example.com"
}
```

```bash
curl -X POST "${BASE_URL}/api/accounts" \
  -H "Authorization: Bearer ${MANAGEMENT_TOKEN}" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Alice","phone":"09123456789","email":"alice@example.com"}'
```

##### Response — Empty Body

| Field | Type | Sample Value | Definition |
| --- | --- | --- | --- |
| HTTP status | HTTP status | `200 OK` | Operation completed successfully. |
| Body | None | Empty | No response object is returned; do not parse JSON. |

```http
HTTP/1.1 200 OK

```

#### Update a management account

`PUT /api/accounts/{id}`

Updates name and phone while preserving status, registeredAt, and activatedAt. Submit the existing email unchanged; changing email returns 400 because identity synchronization is not implemented. Unknown account IDs return 404.

##### Path Parameters

| Field | Type | Location | Required | Sample Value | Definition |
| --- | --- | --- | --- | --- | --- |
| `id` | `UUID` | Path | Yes | `550e8400-e29b-41d4-a716-446655440000` | Local management account UUID. |

##### Request Form — `AccountRegistrationForm`

| Field | Type | Location | Required | Sample Value | Definition |
| --- | --- | --- | --- | --- | --- |
| `name` | `String` | JSON | Yes | `Alice` | Nonblank name, maximum 255 characters. |
| `phone` | `String` | JSON | Yes | `09123456789` | Nonblank phone, maximum 255 characters. |
| `email` | `String` | JSON | Yes | `alice@example.com` | Valid nonblank email, maximum 255 characters; exact matching. Must remain unchanged on update. |

```json
{
  "name": "Alice",
  "phone": "09123456789",
  "email": "alice@example.com"
}
```

```bash
curl -X PUT "${BASE_URL}/api/accounts/550e8400-e29b-41d4-a716-446655440000" \
  -H "Authorization: Bearer ${MANAGEMENT_TOKEN}" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Alice","phone":"09123456789","email":"alice@example.com"}'
```

##### Response — Empty Body

| Field | Type | Sample Value | Definition |
| --- | --- | --- | --- |
| HTTP status | HTTP status | `200 OK` | Operation completed successfully. |
| Body | None | Empty | No response object is returned; do not parse JSON. |

```http
HTTP/1.1 200 OK

```

### Client System

#### List configured systems

`GET /api/setting/systems`

Returns all systems from `app.client.systems` in configuration order, including systems that already have PIN lock settings. Request body, path parameters, and query parameters: none.

##### Response DTO — `List<String>`

| Field | Type | Sample Value | Definition |
| --- | --- | --- | --- |
| Response body | `List<String>` | `["Consumer", "Management"]` | All configured client systems; an empty configuration returns `[]`. |

##### List Item — `String`

| Field | Type | Sample Value | Definition |
| --- | --- | --- | --- |
| Item value | `String` | `Consumer` | Case-sensitive configured system name. |

```bash
curl "${BASE_URL}/api/setting/systems" \
  -H "Authorization: Bearer ${MANAGEMENT_TOKEN}"
```

Response: `200 OK`, `application/json`.

```json
["Consumer", "Management"]
```

### PIN Lock Setting

#### List PIN lock settings

`GET /api/setting/pin-locks`

Returns non-deleted settings sorted by system name ascending. Request body, path parameters, and query parameters: none.

##### Response DTO — `List<PinLockSettingDto>`

The response is a JSON array of `PinLockSettingDto` records. No matching records returns `[]`.

##### List Item — `PinLockSettingDto`

| Field | Type | Sample Value | Definition |
| --- | --- | --- | --- |
| `system` | `String` | `Consumer` | Client system associated with the policy. |
| `tempLockFailsCount` | `int` | `3` | Failed PIN threshold for a temporary lock. |
| `tempLockWaitMinutes` | `int` | `15` | Temporary-lock duration in minutes. |
| `permanentLockFailsCount` | `int` | `9` | Failed PIN threshold for a permanent lock. |

```bash
curl "${BASE_URL}/api/setting/pin-locks" \
  -H "Authorization: Bearer ${MANAGEMENT_TOKEN}"
```

Response: `200 OK`, `application/json`.

```json
[
  {
    "system": "Consumer",
    "tempLockFailsCount": 3,
    "tempLockWaitMinutes": 15,
    "permanentLockFailsCount": 9
  }
]
```

#### List systems available for new settings

`GET /api/setting/pin-locks/systems`

Returns configured systems without non-deleted PIN lock settings. Results are distinct and sorted ascending. A system with a deleted record is available again. This differs from `/api/setting/systems`, which lists all configured systems.

Request body, path parameters, and query parameters: none.

##### Response DTO — `List<String>`

| Field | Type | Sample Value | Definition |
| --- | --- | --- | --- |
| Response body | `List<String>` | `["Management"]` | Systems available for creating settings; `[]` if none are available. |

##### List Item — `String`

| Field | Type | Sample Value | Definition |
| --- | --- | --- | --- |
| Item value | `String` | `Management` | Configured system with no non-deleted settings. |

```bash
curl "${BASE_URL}/api/setting/pin-locks/systems" \
  -H "Authorization: Bearer ${MANAGEMENT_TOKEN}"
```

Response: `200 OK`, `application/json`. Example after creating Consumer settings:

```json
["Management"]
```

#### Create PIN lock settings

`POST /api/setting/pin-locks/{system}`

Creates settings for a configured system. Existing non-deleted settings return 400. If a deleted record exists, creation restores it and replaces its policy values.

##### Path Parameters

| Field | Type | Location | Required | Sample Value | Definition |
| --- | --- | --- | --- | --- | --- |
| `system` | `String` | Path | Yes | `Consumer` | Case-sensitive name in `app.client.systems`. |

##### Request Form — `PinLockSettingForm`

| Field | Type | Location | Required | Sample Value | Definition |
| --- | --- | --- | --- | --- | --- |
| `tempLockFailsCount` | `int` | JSON | Yes | `3` | Positive failure threshold for a temporary lock; minimum 1. |
| `tempLockWaitMinutes` | `int` | JSON | Yes | `15` | Positive temporary-lock duration in minutes; minimum 1. |
| `permanentLockFailsCount` | `int` | JSON | Yes | `9` | Positive failure threshold for a permanent lock; must exceed tempLockFailsCount. |

Sample request:

```json
{
  "tempLockFailsCount": 3,
  "tempLockWaitMinutes": 15,
  "permanentLockFailsCount": 9
}
```

```bash
curl -X POST "${BASE_URL}/api/setting/pin-locks/Consumer" \
  -H "Authorization: Bearer ${MANAGEMENT_TOKEN}" \
  -H 'Content-Type: application/json' \
  -d '{"tempLockFailsCount":3,"tempLockWaitMinutes":15,"permanentLockFailsCount":9}'
```

##### Response — Empty Body

| Field | Type | Sample Value | Definition |
| --- | --- | --- | --- |
| HTTP status | HTTP status | `200 OK` | Operation completed successfully. |
| Body | None | Empty | No response object is returned; do not parse JSON. |

Sample response:

```http
HTTP/1.1 200 OK

```

#### Update PIN lock settings

`PUT /api/setting/pin-locks/{system}`

Replaces all policy values for an existing non-deleted record. Missing or deleted settings return 404. The system must still be configured; an unknown system returns 400.

##### Path Parameters

| Field | Type | Location | Required | Sample Value | Definition |
| --- | --- | --- | --- | --- | --- |
| `system` | `String` | Path | Yes | `Consumer` | Case-sensitive name in `app.client.systems`. |

##### Request Form — `PinLockSettingForm`

| Field | Type | Location | Required | Sample Value | Definition |
| --- | --- | --- | --- | --- | --- |
| `tempLockFailsCount` | `int` | JSON | Yes | `3` | Positive failure threshold for a temporary lock; minimum 1. |
| `tempLockWaitMinutes` | `int` | JSON | Yes | `15` | Positive temporary-lock duration in minutes; minimum 1. |
| `permanentLockFailsCount` | `int` | JSON | Yes | `9` | Positive failure threshold for a permanent lock; must exceed tempLockFailsCount. |

Sample request:

```json
{
  "tempLockFailsCount": 3,
  "tempLockWaitMinutes": 15,
  "permanentLockFailsCount": 9
}
```

```bash
curl -X PUT "${BASE_URL}/api/setting/pin-locks/Consumer" \
  -H "Authorization: Bearer ${MANAGEMENT_TOKEN}" \
  -H 'Content-Type: application/json' \
  -d '{"tempLockFailsCount":3,"tempLockWaitMinutes":15,"permanentLockFailsCount":9}'
```

##### Response — Empty Body

| Field | Type | Sample Value | Definition |
| --- | --- | --- | --- |
| HTTP status | HTTP status | `200 OK` | Operation completed successfully. |
| Body | None | Empty | No response object is returned; do not parse JSON. |

Sample response:

```http
HTTP/1.1 200 OK

```

## Error responses

Controller errors use `application/problem+json`. Request-field validation is handled by Spring MVC. Security-filter failures may use Spring Security’s response format and may have an empty body; do not assume every 401/403 response contains JSON.

| HTTP status | Meaning |
| --- | --- |
| 400 | Invalid fields, dates, pagination or thresholds; duplicate account/profile/settings; email change; unknown system. |
| 401 | Missing, invalid, expired, or revoked token. |
| 403 | Token lacks the required management.api scope. |
| 404 | Account not found, or settings to update do not exist or are deleted. |
| 409 | Database integrity conflict. |
| 503 | Auth-server email availability check failed; no account is created. |

### Response DTO — `ProblemDetail`

| Field | Type | Sample Value | Definition |
| --- | --- | --- | --- |
| `type` | `URI` | `about:blank` | Problem type identifier. |
| `title` | `String` | `Not Found` | Human-readable HTTP error title. |
| `status` | `int` | `404` | HTTP status code. |
| `detail` | `String` | `PIN lock settings were not found for this system.` | Explanation of the failure. |
| `instance` | `URI` | `/api/setting/pin-locks/Consumer` | Request path associated with the problem. |

Example: updating a configured system with no existing settings:

```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "PIN lock settings were not found for this system.",
  "instance": "/api/setting/pin-locks/Consumer"
}
```

## Implementation notes

`AccountManagementService` validates pagination and date ranges, coordinates repository calls, and handles account business rules and transactions. `AccountQuery` builds the JPA metamodel predicates, DTO projection, sorting, and count query. Each query gets its own root and fresh predicates. `AccountSearch` and `AccountListItem` remain records without JPA query logic.

## Tests

From the project root:

```bash
mvn -pl management-service clean test
```

Tests cover full application-context startup, configured JPA transactions, PIN lock and account APIs, resource security, configuration validation, repository queries, and schema validation. They use mocked HTTP/repositories and embedded H2; Docker is not required. They do not exercise a live PostgreSQL/Kafka deployment.

Run the complete project suite with `mvn clean test` from the project root. Schema tests validate the initialization SQL against entity mappings.

## Current boundaries

These APIs store policy and publish versioned change events for Auth Server. Failure counting, lock enforcement, and automatic unlocking are Auth Server responsibilities. No delete endpoint is provided, although reads exclude deleted records and creation can restore them.

Management account search, local creation, and updates are implemented. Accounts remain Pending until the future activation-event consumer is implemented. Creation does not yet start the invitation workflow. Email changes require a separate identity-synchronization workflow. There are no Consumer Mobile endpoints in this service; those belong to consumer-service.

Security rules enforce scopes. Fine-grained management role permissions remain a separate design decision.
