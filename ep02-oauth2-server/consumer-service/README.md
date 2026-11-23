# Consumer Service

Manages Consumer account registration, approval, profiles, access history, and PIN lock state. Default base URL: `http://localhost:8081`.

## Contents

- [Run locally](#run-locally)
- [Configuration](#configuration)
- [Database information](#database-information)
- [Request conventions](#request-conventions)
- [Management Endpoints](#management-endpoints)
- [Consumer Mobile Endpoints](#consumer-mobile-endpoints)
- [Error responses](#error-responses)
- [Implementation notes](#implementation-notes)
- [Tests](#tests)
- [Current boundaries](#current-boundaries)

## Run locally

Requires Java 25, Maven, and Docker Compose. From the project root:

```bash
export POSTGRES_PASSWORD=postgres
docker compose up -d
mvn -pl consumer-service spring-boot:run
```

Default base URL: `http://localhost:8081`. Auth-server must be running for authenticated API requests. See [auth-server](../auth-server/README.md) for obtaining tokens. Set `SERVER_PORT` separately for each service process.

PostgreSQL initializes `postgres/01-init.sql` and `postgres/03-consumer-schema.sql` only when its database volume is empty. Editing SQL does not modify an existing database. Hibernate uses `ddl-auto: validate`; no migration or automatic table creation/update is enabled. During this planning phase, update the initialization SQL directly. If you intentionally recreate the development database, `docker compose down -v` removes all Compose volumes, including database and Kafka data.

Build and run an executable JAR:

```bash
mvn -pl consumer-service clean package
java -jar consumer-service/target/consumer-service-0.0.1-SNAPSHOT.jar
```

Applications run on the host by default; infrastructure runs in Docker. Container deployments need connection-address overrides. [Consumer service](../consumer-service/README.md) uses port 8081, [Management service](../management-service/README.md) uses 8082, and auth-server uses 8080.

## Configuration

Defaults are in [`application.yml`](src/main/resources/application.yml).

| Environment variable | Default | Purpose |
| --- | --- | --- |
| `SERVER_PORT` | 8081 | HTTP port. |
| `CONSUMER_DB_URL` | `jdbc:postgresql://localhost:5432/wallet_demo?currentSchema=consumer` | JDBC URL. |
| `CONSUMER_DB_USERNAME` | `csmuser` | Service database role. |
| `CONSUMER_DB_PASSWORD` | `csmpass` | Development database password. |
| `AUTH_INTROSPECTION_URI` | `http://localhost:8080/api/internal/tokens/introspect` | Live token-validation endpoint. |
| `AUTH_ISSUER` | `http://localhost:8080` | Expected issuer. |
| `CONSUMER_RESOURCE_ID` | `consumer-service` | Resource ID registered in auth-server. |
| `CONSUMER_RESOURCE_SECRET` | `consumer-resource-secret` | Resource credential; must match auth-server. |
| `AUTH_AUDIENCES` | `consumer.api,management.api` | Accepted audiences; comma-separated list. |
| `AUTH_CONNECT_TIMEOUT` / `AUTH_READ_TIMEOUT` | `3s` / `3s` | Positive HTTP timeouts. |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker. |
| `KAFKA_CONSUMER_GROUP_ID` | `consumer-service` | Kafka consumer group. |
| `PIN_LOCK_ACTIVITY_TOPIC` | `pin-lock-activity-changed.v1` | PIN lock activity topic. |
| `PIN_LOCK_ACTIVITY_CONSUMER_GROUP` | `consumer-service-pin-lock-history` | Consumer Service's independent history consumer group. |
| `AUTH_PIN_LOCK_UNLOCK_URI` | `http://localhost:8080/api/internal/pin-locks/unlock` | Auth Server's internal permanent-unlock endpoint. |
| `KAFKA_OUTBOX_FIXED_DELAY` | `1000` | Delay in milliseconds between outbox publisher polls. |

Custom security settings are bound to validated `AuthenticationProps` under `app.auth`:

```yaml
app:
  auth:
    introspection-uri: ${AUTH_INTROSPECTION_URI:http://localhost:8080/api/internal/tokens/introspect}
    issuer: ${AUTH_ISSUER:http://localhost:8080}
    resource-id: consumer-service
    resource-secret: ${CONSUMER_RESOURCE_SECRET:consumer-resource-secret}
    audiences: [consumer.api, management.api]
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

The PostgreSQL defaults use `wallet_demo`, schema `consumer`, and role `csmuser` with development password `csmpass`. Override with `CONSUMER_DB_URL`, `CONSUMER_DB_USERNAME`, and `CONSUMER_DB_PASSWORD`. Hibernate uses `ddl-auto: validate` and `open-in-view: false`; tables are created by the scripts under `postgres`. Docker init scripts run only for a new database volume, so recreate the local volume after schema changes during development.

Kafka defaults to `localhost:9092`, String keys/values (including JSON text), record acknowledgment, and idempotent producer settings. The listener uses its own group, `consumer-service-pin-lock-history`, so it independently receives Auth Server PIN lock activity events. It records Consumer-system activity with event-ID deduplication; temporary lock history includes `lockedUntil`, so no expiry scheduler is needed. The service also has a generic transactional outbox table, repository, and scheduled `OutboxEventPublisher` for future Consumer-produced events. Polling cadence is configurable with `KAFKA_OUTBOX_FIXED_DELAY`. No extra datasource/Kafka bean configuration is needed unless customized behavior is introduced.

Host defaults assume services run on the host and infrastructure runs in Docker Compose. For container deployments, override localhost connection addresses appropriately.

`POSTGRES_PASSWORD` configures the container superuser, not the service role password in `01-init.sql`. Keep SQL credentials and application settings consistent. Development credentials are examples; replace them for deployment. This service does not send email directly; auth-server owns SMTP and OTP delivery.

## Database information

### Connection and ownership

| Setting | Local development value | Definition |
| --- | --- | --- |
| Engine | PostgreSQL 17 | Docker Compose database. |
| Host and port | `localhost:5432` | Host application connection. |
| Database | `wallet_demo` | Shared development database with service-specific schemas. |
| Schema | `consumer` | Owned exclusively by this service. |
| Database role | `csmuser` | Role owns the schema and tables. |
| Password | `csmpass` | Development password from 01-init.sql. |
| JDBC URL | `jdbc:postgresql://localhost:5432/wallet_demo?currentSchema=consumer` | Selects the default schema. |
| Hibernate DDL mode | `validate` | Checks mappings without changing tables. |

The role search path and JDBC currentSchema select `consumer`; Hibernate also uses `hibernate.default_schema=consumer`. Cross-service access uses APIs and future events, rather than direct reads of other services’ tables. Subsystem account UUIDs are independent of auth-profile UUIDs.

### Tables

| Table | Primary key | Purpose |
| --- | --- | --- |
| `account` | `id` — UUID | Consumer information, registration origin, approval state and timestamps. |
| `account_access` | `account_id` — UUID | Current Login/Logout status and change instant; shared account ID. |
| `access_history` | `account_id, revision` | Login/logout events with a per-account revision. |
| `account_lock_status` | `account_id` — UUID | Current Active/Locked state and change instant; shared account ID. |
| `pin_lock_history` | `id` — UUID | Temporary/Permanent lock and release activity received from Auth Server. |
| `outbox_event` | `id` — UUID | Durable event payloads waiting to be published, with publish and retry status. |

### Table definitions

Column names and PostgreSQL types below follow the initialization SQL. **Nullable** describes database nullability. **Default** is the SQL default, not a Java initializer or service-assigned value; `—` means no explicit SQL default. PK = primary key, FK = foreign key. Primary-key columns are implicitly NOT NULL.

#### `consumer.account`

| Column | PostgreSQL Type | Nullable | Default | Key / Constraints | Definition |
| --- | --- | --- | --- | --- | --- |
| `id` | `UUID` | No | — | PK | Record UUID generated by Hibernate; no database UUID default. |
| `email` | `VARCHAR(255)` | No | — | UNIQUE | Email associated with this record. |
| `name` | `VARCHAR(255)` | Yes | — | — | Consumer name; nullable in the current schema. |
| `phone` | `VARCHAR(255)` | No | — | — | Account phone number. |
| `address` | `VARCHAR(255)` | No | — | — | Consumer address. |
| `date_of_birth` | `DATE` | No | — | — | Date of birth; date-only value without a time zone. |
| `registration_type` | `SMALLINT` | No | — | CHECK (registration_type BETWEEN 0 AND 1) | Registration origin: 0 Self, 1 Management. |
| `status` | `SMALLINT` | No | — | CHECK (status BETWEEN 0 AND 2) | Account state: 0 Applied, 1 Approved, 2 Rejected. |
| `registered_at` | `TIMESTAMP(6) WITH TIME ZONE` | No | — | — | Registration instant. |
| `approved_at` | `TIMESTAMP(6) WITH TIME ZONE` | Yes | — | — | Approval instant; null until approved. |
| `rejected_at` | `TIMESTAMP(6) WITH TIME ZONE` | Yes | — | — | Rejection instant; null unless rejected. |

Table constraints and indexes:

| Name | Definition |
| --- | --- |
| `ck_account_registration_type` | `CHECK (registration_type BETWEEN 0 AND 1)` |
| `ck_account_status` | `CHECK (status BETWEEN 0 AND 2)` |

#### `consumer.account_access`

| Column | PostgreSQL Type | Nullable | Default | Key / Constraints | Definition |
| --- | --- | --- | --- | --- | --- |
| `account_id` | `UUID` | No | — | PK; FK → consumer.account.id | Consumer account UUID referenced by this record. |
| `status` | `SMALLINT` | No | — | CHECK (status BETWEEN 0 AND 1) | Current access state: 0 Login, 1 Logout. |
| `status_change_at` | `TIMESTAMP(6) WITH TIME ZONE` | No | — | — | Instant of this state change or history event. |

Table constraints and indexes:

| Name | Definition |
| --- | --- |
| `ck_account_access_status` | `CHECK (status BETWEEN 0 AND 1)` |

#### `consumer.access_history`

| Column | PostgreSQL Type | Nullable | Default | Key / Constraints | Definition |
| --- | --- | --- | --- | --- | --- |
| `account_id` | `UUID` | No | — | PK (composite); FK → consumer.account.id | Consumer account UUID referenced by this record. |
| `revision` | `INTEGER` | No | — | PK (composite) | Revision within this account’s access history; part of the composite key. |
| `status` | `SMALLINT` | No | — | CHECK (status BETWEEN 0 AND 1) | Recorded access event: 0 Login, 1 Logout. |
| `status_change_at` | `TIMESTAMP(6) WITH TIME ZONE` | No | — | — | Instant of this state change or history event. |

Table constraints and indexes:

| Name | Definition |
| --- | --- |
| `Unnamed table constraint` | `PRIMARY KEY (account_id, revision)` |
| `ck_access_history_status` | `CHECK (status BETWEEN 0 AND 1)` |

#### `consumer.account_lock_status`

| Column | PostgreSQL Type | Nullable | Default | Key / Constraints | Definition |
| --- | --- | --- | --- | --- | --- |
| `account_id` | `UUID` | No | — | PK; FK → consumer.account.id | Consumer account UUID referenced by this record. |
| `status` | `SMALLINT` | No | — | CHECK (status BETWEEN 0 AND 1) | Current lock state: 0 Active, 1 Locked. |
| `status_change_at` | `TIMESTAMP(6) WITH TIME ZONE` | No | — | — | Instant of this state change or history event. |

Table constraints and indexes:

| Name | Definition |
| --- | --- |
| `ck_account_lock_status` | `CHECK (status BETWEEN 0 AND 1)` |

#### `consumer.pin_lock_history`

| Column | PostgreSQL Type | Nullable | Default | Key / Constraints | Definition |
| --- | --- | --- | --- | --- | --- |
| `id` | `UUID` | No | — | PK | Record UUID generated by Hibernate; no database UUID default. |
| `account_id` | `UUID` | No | — | FK → consumer.account.id | Consumer account UUID referenced by this record. |
| `event_id` | `UUID` | No | — | UNIQUE | Auth Server event ID, used to make Kafka delivery idempotent. |
| `lock_type` | `SMALLINT` | No | — | CHECK (lock_type BETWEEN 0 AND 2) | Activity type: 0 Temporary, 1 Permenant, 2 Released. |
| `lock_at` | `TIMESTAMP(6) WITH TIME ZONE` | No | — | — | Instant when the lock event occurred. |
| `locked_until` | `TIMESTAMP(6) WITH TIME ZONE` | Yes | — | — | Expiry instant for a temporary lock. |
| `requested_by` | `VARCHAR(255)` | Yes | — | — | Management principal that requested a permanent unlock. |

#### `consumer.outbox_event`

| Column | PostgreSQL Type | Nullable | Default | Key / Constraints | Definition |
| --- | --- | --- | --- | --- | --- |
| `id` | `UUID` | No | — | PK | Event UUID, assigned by the event producer. |
| `topic` | `VARCHAR(100)` | No | — | — | Kafka topic for this event. |
| `aggregate_key` | `VARCHAR(255)` | No | — | — | Kafka message key, generally the aggregate identifier. |
| `payload` | `TEXT` | No | — | — | Serialized event payload. |
| `created_at` | `TIMESTAMP(6) WITH TIME ZONE` | No | — | — | When the event was enqueued. |
| `published_at` | `TIMESTAMP(6) WITH TIME ZONE` | Yes | — | — | Set after Kafka acknowledges publication. |
| `attempt_count` | `INTEGER` | No | — | — | Number of failed publication attempts. |
| `last_error` | `TEXT` | Yes | — | — | Most recent publication error, if any. |

Table constraints and indexes:

| Name | Definition |
| --- | --- |
| `ck_pin_lock_history_type` | `CHECK (lock_type BETWEEN 0 AND 2)` |
| `ix_pin_lock_history_account_lock_at` | `INDEX (account_id, lock_at)` |
| `ix_consumer_outbox_event_pending` | `INDEX (published_at, created_at)` |

### Relationships and constraints

Unique account email prevents duplicate local registrations. Access and lock tables reference account.id; current-state records share its UUID through @MapsId. History rows have foreign keys to their account. PIN lock history has an index on account_id and lock_at. There is no database foreign key to auth-server profiles.

### Stored identifiers and status values

| Column | Stored values |
| --- | --- |
| `account.registration_type` | 0 = Self; 1 = Management. |
| `account.status` | 0 = Applied; 1 = Approved; 2 = Rejected. |
| `account_access.status`, `access_history.status` | 0 = Login; 1 = Logout. |
| `account_lock_status.status` | 0 = Active; 1 = Locked. |
| `pin_lock_history.lock_type` | 0 = Temporary; 1 = Permenant (current enum spelling). |


### Security data and timestamps

Passwords and OAuth tokens belong to auth-server and are not stored in these tables. Names, emails, phones, and other personal information are stored as ordinary columns in this demo. Event timestamps use Instant / TIMESTAMP WITH TIME ZONE. Date of birth uses LocalDate / DATE. Status enums use ordinal SMALLINT values; keep enum order and SQL constraints aligned.

### Initialization and inspection

[`01-init.sql`](../postgres/01-init.sql) creates service roles and schemas; [`03-consumer-schema.sql`](../postgres/03-consumer-schema.sql) creates the tables. Initialization runs only for an empty PostgreSQL volume.

```bash
docker compose exec postgres psql -U csmuser -d wallet_demo
```

Inside psql:

```text
\dt consumer.*
\d consumer.account
```

## Request conventions

All documented endpoints require a bearer access token. Management endpoints require `management.api`; Consumer Mobile endpoints require `consumer.api`. Tokens are validated through auth-server introspection on every request. Management operations currently enforce scopes rather than fine-grained roles.

```bash
export BASE_URL='http://localhost:8081'
export MANAGEMENT_TOKEN='<management-access-token>'
export CONSUMER_TOKEN='<consumer-access-token>'
```

Use `Content-Type: application/json` for JSON request bodies. Dates use `YYYY-MM-DD`; event timestamps use ISO-8601 UTC instants. UUIDs, emails, tokens, and timestamps in examples are illustrative. An empty response is not `{}` or `null`.

### Response DTO — `Page<T>`

| Field | Type | Sample Value | Definition |
| --- | --- | --- | --- |
| `content` | `List<T>` | `[]` | Items in a paginated search response; item fields are documented per endpoint. |
| `number` | `int` | `0` | Current zero-based page. |
| `size` | `int` | `10` | Requested page size. |
| `numberOfElements` | `int` | `1` | Items on the current page. |
| `totalElements` | `long` | `1` | Total matching items across all pages. |
| `totalPages` | `int` | `1` | Total matching pages. |
| `first / last / empty` | `boolean` | `true / true / false` | Page position and empty-result indicators. |

## Management Endpoints

For authenticated management users reviewing Consumer accounts and operational history.

| Resource | Method | Endpoint | Description |
| --- | --- | --- | --- |
| Account | GET | `/api/management/consumer` | Search accounts. |
| Account | POST | `/api/management/consumer` | Register an account on behalf of a consumer. |
| Account | PUT | `/api/management/consumer/{id}/approve` | Approve an Applied account. |
| Account | PUT | `/api/management/consumer/{id}/reject` | Reject an Applied account. |
| Access History | GET | `/api/management/consumer/access` | Search login/logout events. |
| PIN Lock | GET | `/api/management/consumer/lock` | Search lock events and current lock state. |
| PIN Lock | PUT | `/api/management/consumer/lock/{id}/unlock` | Request Auth Server to unlock a permanent PIN lock. |

### Account

#### Search accounts

`GET /api/management/consumer`

Filters registration records and returns a page, ordered by registration instant descending and account UUID ascending. No request body.

##### Search Form — `ConsumerAccountSearch`

| Field | Type | Location | Required | Sample Value | Definition |
| --- | --- | --- | --- | --- | --- |
| `status` | `Account.Status` | Query | No | `Applied` | Account status: Applied, Approved, or Rejected. |
| `registrationType` | `Account.RegistrationType` | Query | No | `Self` | Registration origin: Self or Management. |
| `createdFrom` | `LocalDate` | Query | No | `2026-11-01` | Inclusive registration date in UTC. |
| `createdTo` | `LocalDate` | Query | No | `2026-11-30` | Inclusive registration date in UTC; must not precede createdFrom. |
| `keyword` | `String` | Query | No | `alice` | Case-insensitive literal prefix of name, email, or phone. SQL wildcards are escaped. |

##### Pagination Parameters

These controller parameters are separate from the search-form record.

| Field | Type | Location | Required | Sample Value | Definition |
| --- | --- | --- | --- | --- | --- |
| `page` | `int` | Query | No | `0` | Zero-based page index; default 0, must not be negative. |
| `size` | `int` | Query | No | `10` | Page size; default 10, must be positive. |

##### Response DTO — `Page<ConsumerAccountListItem>`

The page `content` contains `ConsumerAccountListItem` records. Pagination fields are documented under Request conventions.

##### List Item — `ConsumerAccountListItem`

| Field | Type | Sample Value | Definition |
| --- | --- | --- | --- |
| `id` | `UUID` | `550e8400-e29b-41d4-a716-446655440000` | Consumer account UUID; independent of auth-profile UUID. |
| `name` | `String (nullable)` | `Alice` | Consumer name; may be null for an existing incomplete record. |
| `phone` | `String` | `09123456789` | Phone number. |
| `email` | `String` | `alice@example.com` | Email address. |
| `status` | `Account.Status` | `Applied` | Applied, Approved, or Rejected. |
| `registrationType` | `Account.RegistrationType` | `Self` | Self or Management. |
| `registeredAt` | `Instant` | `2026-11-22T06:30:00Z` | Registration instant (field name is registeredAt). |
| `approvedAt` | `Instant (nullable)` | `null` | Approval instant; null until approved. |

```bash
curl --get "${BASE_URL}/api/management/consumer" \
  -H "Authorization: Bearer ${MANAGEMENT_TOKEN}" \
  --data-urlencode 'page=0' \
  --data-urlencode 'size=10' \
  --data-urlencode 'status=Applied' \
  --data-urlencode 'registrationType=Self' \
  --data-urlencode 'createdFrom=2026-11-01' \
  --data-urlencode 'createdTo=2026-11-30' \
  --data-urlencode 'keyword=alice'
```

Response: `200 OK`, `application/json`. The JSON below shows the item and principal page fields; direct Spring Data Page serialization may also include `pageable`, `sort`, and other pagination metadata.

```json
{
  "content": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "name": "Alice",
      "phone": "09123456789",
      "email": "alice@example.com",
      "status": "Applied",
      "registrationType": "Self",
      "registeredAt": "2026-11-22T06:30:00Z",
      "approvedAt": null
    }
  ],
  "number": 0,
  "size": 10,
  "numberOfElements": 1,
  "totalElements": 1,
  "totalPages": 1,
  "first": true,
  "last": true,
  "empty": false
}
```

#### Register a consumer

`POST /api/management/consumer`

Creates an Applied account with registration type Management. Initializes access status to Logout and lock status to Active in the same transaction. Rejects duplicate emails in Consumer Service and when Auth Server already has a Consumer profile. Approval creates a durable event; Auth Server then creates the time-limited invitation and queues its email for delivery.

##### Request Form — `ConsumerRegistrationForm`

| Field | Type | Location | Required | Sample Value | Definition |
| --- | --- | --- | --- | --- | --- |
| `name` | `String` | JSON | Yes | `Alice` | Consumer name; nonblank, maximum 255 characters. |
| `email` | `String` | JSON | Yes | `alice@example.com` | Valid nonblank email, maximum 255 characters; matched exactly. |
| `phone` | `String` | JSON | Yes | `09123456789` | Nonblank phone, maximum 255 characters. |
| `dob` | `LocalDate` | JSON | Yes | `1990-01-01` | Date of birth as YYYY-MM-DD; must be in the past. |
| `address` | `String` | JSON | Yes | `Yangon` | Nonblank address, maximum 255 characters. |

Sample request:

```json
{
  "name": "Alice",
  "email": "alice@example.com",
  "phone": "09123456789",
  "dob": "1990-01-01",
  "address": "Yangon"
}
```

```bash
curl -X POST "${BASE_URL}/api/management/consumer" \
  -H "Authorization: Bearer ${MANAGEMENT_TOKEN}" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Alice","email":"alice@example.com","phone":"09123456789","dob":"1990-01-01","address":"Yangon"}'
```

##### Response — Empty Body

| Field | Type | Sample Value | Definition |
| --- | --- | --- | --- |
| `HTTP status` | `HTTP status` | `200 OK` | Operation completed successfully. |
| `Body` | `None` | Empty | No response object is returned; do not parse JSON. |

Sample response:

```http
HTTP/1.1 200 OK

```

#### Approve a consumer

`PUT /api/management/consumer/{id}/approve`

Changes Applied to Approved and records approvedAt. Repeated approval returns 400; an unknown account returns 404. Approval preserves access and lock states. A transactional outbox publishes the approval event. For self-registration, Auth Server activates the Pending profile. For management registration, Auth Server creates an invitation registration record and durably queues the invitation email.

##### Path Parameters

| Field | Type | Location | Required | Sample Value | Definition |
| --- | --- | --- | --- | --- | --- |
| `id` | `UUID` | Path | Yes | `550e8400-e29b-41d4-a716-446655440000` | Consumer account UUID, not the auth-profile UUID. |

Request body: none.

```bash
curl -X PUT "${BASE_URL}/api/management/consumer/550e8400-e29b-41d4-a716-446655440000/approve" \
  -H "Authorization: Bearer ${MANAGEMENT_TOKEN}"
```

#### Reject a consumer

`PUT /api/management/consumer/{id}/reject`

Changes an Applied account to Rejected, records `rejectedAt`, and writes a rejection event to the transactional outbox. For self-registration, Auth Server closes the related Pending profile. Only Applied accounts can be rejected; repeated or otherwise invalid transitions return 400.

```bash
curl -X PUT "${BASE_URL}/api/management/consumer/550e8400-e29b-41d4-a716-446655440000/reject" \
  -H "Authorization: Bearer ${MANAGEMENT_TOKEN}"
```

##### Response — Empty Body

| Field | Type | Sample Value | Definition |
| --- | --- | --- | --- |
| `HTTP status` | `HTTP status` | `200 OK` | Operation completed successfully. |
| `Body` | `None` | Empty | No response object is returned; do not parse JSON. |

Sample response:

```http
HTTP/1.1 200 OK

```

### Access History

#### Search access history

`GET /api/management/consumer/access`

Returns login/logout events, ordered by event instant descending, account UUID ascending, then revision descending. Dates filter the access event, not registration. No request body.

##### Search Form — `AccessHistorySearch`

| Field | Type | Location | Required | Sample Value | Definition |
| --- | --- | --- | --- | --- | --- |
| `status` | `AccountAccess.Status` | Query | No | `Login` | Access event status: Login or Logout. |
| `dateFrom` | `LocalDate` | Query | No | `2026-11-01` | Inclusive event date in UTC, YYYY-MM-DD. |
| `dateTo` | `LocalDate` | Query | No | `2026-11-30` | Inclusive event date in UTC; must not precede dateFrom. |
| `keyword` | `String` | Query | No | `alice` | Case-insensitive literal prefix of name, email, or phone. SQL wildcards are escaped. |

##### Pagination Parameters

These controller parameters are separate from the search-form record.

| Field | Type | Location | Required | Sample Value | Definition |
| --- | --- | --- | --- | --- | --- |
| `page` | `int` | Query | No | `0` | Zero-based page index; default 0, must not be negative. |
| `size` | `int` | Query | No | `10` | Page size; default 10, must be positive. |

##### Response DTO — `Page<AccessHistoryListItem>`

The page `content` contains `AccessHistoryListItem` records. Pagination fields are documented under Request conventions.

##### List Item — `AccessHistoryListItem`

| Field | Type | Sample Value | Definition |
| --- | --- | --- | --- |
| `accountId` | `UUID` | `550e8400-e29b-41d4-a716-446655440000` | Consumer account UUID. |
| `revision` | `int` | `1` | Revision within the account access history. |
| `accessType` | `AccountAccess.Status` | `Login` | Login or Logout event type. |
| `accessAt` | `Instant` | `2026-11-22T07:00:00Z` | Access-event instant. |
| `name` | `String (nullable)` | `Alice` | Current account name. |
| `email` | `String` | `alice@example.com` | Current account email. |
| `status` | `Account.Status` | `Approved` | Current account status: Applied, Approved, or Rejected, distinct from accessType. |
| `registeredAt` | `Instant` | `2026-11-22T06:30:00Z` | Account registration instant. |
| `approvedAt` | `Instant (nullable)` | `2026-11-22T07:00:00Z` | Account approval instant; may be null. |

```bash
curl --get "${BASE_URL}/api/management/consumer/access" \
  -H "Authorization: Bearer ${MANAGEMENT_TOKEN}" \
  --data-urlencode 'page=0' \
  --data-urlencode 'size=10' \
  --data-urlencode 'status=Login' \
  --data-urlencode 'dateFrom=2026-11-01' \
  --data-urlencode 'dateTo=2026-11-30' \
  --data-urlencode 'keyword=alice'
```

Response: `200 OK`, `application/json`. The JSON below shows the item and principal page fields; direct Spring Data Page serialization may also include `pageable`, `sort`, and other pagination metadata.

```json
{
  "content": [
    {
      "accountId": "550e8400-e29b-41d4-a716-446655440000",
      "revision": 1,
      "accessType": "Login",
      "accessAt": "2026-11-22T07:00:00Z",
      "name": "Alice",
      "email": "alice@example.com",
      "status": "Approved",
      "registeredAt": "2026-11-22T06:30:00Z",
      "approvedAt": "2026-11-22T07:00:00Z"
    }
  ],
  "number": 0,
  "size": 10,
  "numberOfElements": 1,
  "totalElements": 1,
  "totalPages": 1,
  "first": true,
  "last": true,
  "empty": false
}
```

### PIN Lock

#### Search PIN lock history

`GET /api/management/consumer/lock`

Returns PIN lock events with each account’s current lock state. Ordered by lock instant descending, account UUID ascending, then event UUID descending. Without a status filter, events with no current lock record are included with null state fields. No request body.

##### Search Form — `LockHistorySearch`

| Field | Type | Location | Required | Sample Value | Definition |
| --- | --- | --- | --- | --- | --- |
| `status` | `AccountLockStatus.Status` | Query | No | `Locked` | Current permanent PIN lock projection: Active or Locked; temporary lock state is managed by Auth Server and is not reflected here. |
| `dateFrom` | `LocalDate` | Query | No | `2026-11-01` | Inclusive event date in UTC, YYYY-MM-DD. |
| `dateTo` | `LocalDate` | Query | No | `2026-11-30` | Inclusive event date in UTC; must not precede dateFrom. |
| `keyword` | `String` | Query | No | `alice` | Case-insensitive literal prefix of name, email, or phone. SQL wildcards are escaped. |

##### Pagination Parameters

These controller parameters are separate from the search-form record.

| Field | Type | Location | Required | Sample Value | Definition |
| --- | --- | --- | --- | --- | --- |
| `page` | `int` | Query | No | `0` | Zero-based page index; default 0, must not be negative. |
| `size` | `int` | Query | No | `10` | Page size; default 10, must be positive. |

##### Response DTO — `Page<LockHistoryListItem>`

The page `content` contains `LockHistoryListItem` records. Pagination fields are documented under Request conventions.

##### List Item — `LockHistoryListItem`

| Field | Type | Sample Value | Definition |
| --- | --- | --- | --- |
| `id` | `UUID` | `6371d411-a999-4b74-a7e3-87b9c7d32202` | Lock-event UUID; do not use this as the unlock account ID. |
| `accountId` | `UUID` | `550e8400-e29b-41d4-a716-446655440000` | Consumer account UUID used for unlocking. |
| `lockType` | `PinLockHistory.LockType` | `Temporary` | Temporary or Permenant (current enum spelling). |
| `lockAt` | `Instant` | `2026-11-22T07:00:00Z` | Instant when the lock event occurred. |
| `lockStatus` | `AccountLockStatus.Status (nullable)` | `Locked` | Current permanent PIN lock projection (Active or Locked); temporary locks remain in Auth Server. May be null when no projection exists. |
| `statusChangeAt` | `Instant (nullable)` | `2026-11-22T07:00:00Z` | Instant of the current lock-state change; may be null. |
| `name` | `String (nullable)` | `Alice` | Current consumer name. |
| `email` | `String` | `alice@example.com` | Current consumer email. |
| `phone` | `String` | `09123456789` | Current consumer phone. |

```bash
curl --get "${BASE_URL}/api/management/consumer/lock" \
  -H "Authorization: Bearer ${MANAGEMENT_TOKEN}" \
  --data-urlencode 'page=0' \
  --data-urlencode 'size=10' \
  --data-urlencode 'status=Locked' \
  --data-urlencode 'dateFrom=2026-11-01' \
  --data-urlencode 'dateTo=2026-11-30' \
  --data-urlencode 'keyword=alice'
```

Response: `200 OK`, `application/json`. The JSON below shows the item and principal page fields; direct Spring Data Page serialization may also include `pageable`, `sort`, and other pagination metadata.

```json
{
  "content": [
    {
      "id": "6371d411-a999-4b74-a7e3-87b9c7d32202",
      "accountId": "550e8400-e29b-41d4-a716-446655440000",
      "lockType": "Temporary",
      "lockAt": "2026-11-22T07:00:00Z",
      "lockStatus": "Locked",
      "statusChangeAt": "2026-11-22T07:00:00Z",
      "name": "Alice",
      "email": "alice@example.com",
      "phone": "09123456789"
    }
  ],
  "number": 0,
  "size": 10,
  "numberOfElements": 1,
  "totalElements": 1,
  "totalPages": 1,
  "first": true,
  "last": true,
  "empty": false
}
```

#### Unlock a consumer

`PUT /api/management/consumer/lock/{id}/unlock`

Requests Auth Server to release the consumer's permanent PIN lock. Auth Server is the source of truth and publishes the unlock event; this service records the event and updates its `AccountLockStatus` projection asynchronously. Temporary PIN locks cannot be released through this endpoint. Releasing a lock preserves PIN lock history, account approval, and access state. Once the unlock event is consumed, past events remain visible, but no longer match the Locked current-status filter.

##### Path Parameters

| Field | Type | Location | Required | Sample Value | Definition |
| --- | --- | --- | --- | --- | --- |
| `id` | `UUID` | Path | Yes | `550e8400-e29b-41d4-a716-446655440000` | Consumer account UUID, not the lock-event UUID. |

Request body: none.

```bash
curl -X PUT "${BASE_URL}/api/management/consumer/lock/550e8400-e29b-41d4-a716-446655440000/unlock" \
  -H "Authorization: Bearer ${MANAGEMENT_TOKEN}"
```

##### Response — Empty Body

| Field | Type | Sample Value | Definition |
| --- | --- | --- | --- |
| `HTTP status` | `HTTP status` | `200 OK` | Operation completed successfully. |
| `Body` | `None` | Empty | No response object is returned; do not parse JSON. |

Sample response:

```http
HTTP/1.1 200 OK

```

## Consumer Mobile Endpoints

For a consumer submitting personal information or viewing their own profile. The trusted username from introspection identifies the account; the token subject is an independent auth-profile UUID.

| Resource | Method | Endpoint | Description |
| --- | --- | --- | --- |
| Registration | POST | `/api/mobile/registration` | Submit the authenticated consumer’s information. |
| Profile | GET | `/api/mobile/profile` | Read the authenticated consumer’s profile. |

### Registration

#### Register consumer information

`POST /api/mobile/registration`

Creates an Applied account with Self registration type, Logout access status, and Active lock status. The email must exactly match the trusted authenticated username; another email returns 403. Duplicate registration returns 400. Requires a Pending Consumer profile and the restricted `consumer.onboarding` scope. The normal Consumer API scope is unavailable until Auth Server processes the approval event and activates the profile.

##### Request Form — `SelfRegistrationForm`

| Field | Type | Location | Required | Sample Value | Definition |
| --- | --- | --- | --- | --- | --- |
| `name` | `String` | JSON | Yes | `Alice` | Consumer name; nonblank, maximum 255 characters. |
| `email` | `String` | JSON | Yes | `alice@example.com` | Valid nonblank email, maximum 255 characters; matched exactly. |
| `phone` | `String` | JSON | Yes | `09123456789` | Nonblank phone, maximum 255 characters. |
| `dob` | `LocalDate` | JSON | Yes | `1990-01-01` | Date of birth as YYYY-MM-DD; must be in the past. |
| `address` | `String` | JSON | Yes | `Yangon` | Nonblank address, maximum 255 characters. |

Sample request:

```json
{
  "name": "Alice",
  "email": "alice@example.com",
  "phone": "09123456789",
  "dob": "1990-01-01",
  "address": "Yangon"
}
```

```bash
curl -X POST "${BASE_URL}/api/mobile/registration" \
  -H "Authorization: Bearer ${CONSUMER_TOKEN}" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Alice","email":"alice@example.com","phone":"09123456789","dob":"1990-01-01","address":"Yangon"}'
```

##### Response — Empty Body

| Field | Type | Sample Value | Definition |
| --- | --- | --- | --- |
| `HTTP status` | `HTTP status` | `200 OK` | Operation completed successfully. |
| `Body` | `None` | Empty | No response object is returned; do not parse JSON. |

Sample response:

```http
HTTP/1.1 200 OK

```

### Profile

#### Get my profile

`GET /api/mobile/profile`

Returns the consumer’s own account, including current lock/access status. No account ID or email may be supplied to select another user.

##### Request Headers

| Field | Type | Location | Required | Sample Value | Definition |
| --- | --- | --- | --- | --- | --- |
| `Authorization` | `String (HTTP header)` | Header | Yes | `Bearer <consumer-access-token>` | Valid token with consumer.api scope and a trusted username attribute. |

Request body and query parameters: none. Missing account returns 404; missing trusted username returns 401.

##### Response DTO — `ConsumerProfile`

| Field | Type | Sample Value | Definition |
| --- | --- | --- | --- |
| `id` | `UUID` | `550e8400-e29b-41d4-a716-446655440000` | Consumer account UUID. |
| `name` | `String (nullable)` | `Alice` | Consumer name; may be null for incomplete existing data. |
| `email` | `String` | `alice@example.com` | Identity email associated with this account. |
| `phone` | `String` | `09123456789` | Phone number. |
| `address` | `String` | `Yangon` | Consumer address. |
| `dateOfBirth` | `LocalDate` | `1990-01-01` | Date of birth (response name differs from request dob). |
| `registrationType` | `Account.RegistrationType` | `Self` | Self or Management. |
| `status` | `Account.Status` | `Approved` | Applied, Approved, or Rejected. |
| `registeredAt` | `Instant` | `2026-11-22T06:30:00Z` | UTC registration instant. |
| `approvedAt` | `Instant (nullable)` | `2026-11-22T07:00:00Z` | UTC approval instant; null until approved. |
| `lockStatus` | `AccountLockStatus.Status (nullable)` | `Active` | Current permanent PIN lock projection (Active or Locked); temporary locks remain in Auth Server. Null if no projection exists. |
| `accessStatus` | `AccountAccess.Status (nullable)` | `Logout` | Login or Logout; null if no current access record exists. |

```bash
curl -X GET "${BASE_URL}/api/mobile/profile" \
  -H "Authorization: Bearer ${CONSUMER_TOKEN}"
```

Response: `200 OK`, `application/json`.

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Alice",
  "email": "alice@example.com",
  "phone": "09123456789",
  "address": "Yangon",
  "dateOfBirth": "1990-01-01",
  "registrationType": "Self",
  "status": "Approved",
  "registeredAt": "2026-11-22T06:30:00Z",
  "approvedAt": "2026-11-22T07:00:00Z",
  "lockStatus": "Active",
  "accessStatus": "Logout"
}
```

## Error responses

Controller errors use `application/problem+json`. Request-field validation is handled by Spring MVC. Security-filter failures may use Spring Security’s response format and may have an empty body; do not assume every 401/403 response contains JSON.

| HTTP status | Meaning |
| --- | --- |
| 400 | Invalid field/UUID/date/page; duplicate account or invalid state transition. |
| 401 | Missing, invalid, expired, or revoked token, or missing trusted username. |
| 403 | Insufficient scope, or self-registration email differs from authenticated username. |
| 404 | Consumer account or current lock-state record not found. |
| 409 | Database uniqueness conflict, including concurrent duplicate creation. |

### Response DTO — `ProblemDetail`

| Field | Type | Sample Value | Definition |
| --- | --- | --- | --- |
| `type` | URI | `about:blank` | Problem type identifier. |
| `title` | String | `Not Found` | HTTP error title. |
| `status` | int | `404` | HTTP status code. |
| `detail` | String | `No consumer account was found for the authenticated user.` | Failure explanation. |
| `instance` | URI | `/api/mobile/profile` | Request path. |

Example: profile lookup for an identity with no Consumer account:

```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "No consumer account was found for the authenticated user.",
  "instance": "/api/mobile/profile"
}
```

## Implementation notes

`ConsumerAccountQuery`, `AccessHistoryQuery`, and `LockHistoryQuery` build JPA metamodel predicates, projections, joins, sorting, and count queries. They create fresh roots and predicates for every query. Services retain search validation, repository execution, transactions, and business operations. Search forms and response records remain independent of JPA.

## Tests

From the project root:

```bash
mvn -pl consumer-service clean test
```

Tests use mocked HTTP/repositories and embedded H2 for Criteria queries and schema validation; they do not require Docker or exercise a live PostgreSQL/Kafka deployment.

Run the complete project suite with `mvn clean test` from the project root. Schema tests validate the initialization SQL against entity mappings.

## Current boundaries

Approval changes the local Consumer account only. Auth-profile activation and event publishing remain unimplemented; no Kafka events or remote account-status updates are sent.

Auth-server currently issues ordinary tokens only for Active profiles. Consequently, a newly registered Pending auth profile cannot yet obtain the consumer token needed for KYC/self-registration. A dedicated, restricted onboarding credential/flow must be designed before that end-to-end signup path works. The mobile endpoints have not been made public to bypass this gap.

The included resource-security rules enforce scopes. Fine-grained management role permissions remain a separate design decision. Access-history and lock-management operations are implemented by AccessHistoryService and LockStatusService.

Local PostgreSQL datasource defaults are provided in application.yml; environment overrides are described below. Tables are defined in `postgres/03-consumer-schema.sql`; Hibernate should validate rather than create/update them.
