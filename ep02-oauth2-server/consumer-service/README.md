# Consumer Service

Manages Consumer account registration, approval, profiles, access history, and PIN lock state. Default base URL: `http://localhost:8081`.

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
| Access History | GET | `/api/management/consumer/access` | Search login/logout events. |
| PIN Lock | GET | `/api/management/consumer/lock` | Search lock events and current lock state. |
| PIN Lock | PUT | `/api/management/consumer/lock/{id}/unlock` | Unlock an account. |

### Account

#### Search accounts

`GET /api/management/consumer`

Filters registration records and returns a page, ordered by registration instant descending and account UUID ascending. No request body.

##### Search Form — `ConsumerAccountSearch`

| Field | Type | Location | Required | Sample Value | Definition |
| --- | --- | --- | --- | --- | --- |
| `status` | `Account.Status` | Query | No | `Applied` | Account status: Applied or Approved. |
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
| `status` | `Account.Status` | `Applied` | Applied or Approved. |
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

Creates an Applied account with registration type Management. Initializes access status to Logout and lock status to Active in the same transaction. Rejects duplicate emails in Consumer service. Auth-server duplication precheck and event-driven invitation are deferred TODOs.

##### Request DTO — `ConsumerRegistrationForm`

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

Changes Applied to Approved and records approvedAt. Repeated approval returns 400; an unknown account returns 404. Approval preserves access and lock states. Auth-profile activation is not performed yet.

##### Path Parameters

| Field | Type | Location | Required | Sample Value | Definition |
| --- | --- | --- | --- | --- | --- |
| `id` | `UUID` | Path | Yes | `550e8400-e29b-41d4-a716-446655440000` | Consumer account UUID, not the auth-profile UUID. |

Request body: none.

```bash
curl -X PUT "${BASE_URL}/api/management/consumer/550e8400-e29b-41d4-a716-446655440000/approve" \
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
| `status` | `Account.Status` | `Approved` | Current account status: Applied or Approved, distinct from accessType. |
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
| `status` | `AccountLockStatus.Status` | Query | No | `Locked` | Current account lock status: Active or Locked; not historical lock type. |
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
| `lockStatus` | `AccountLockStatus.Status (nullable)` | `Locked` | Current Active/Locked state; may be null. |
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

Changes current lock state from Locked to Active and updates statusChangeAt, using a pessimistic row lock. Returns 404 if no current lock record exists, or 400 if already Active. Preserves history, approval, and access state. Auth-server notification remains a TODO. After unlocking, past events remain visible, but no longer match the Locked current-status filter.

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

Creates an Applied account with Self registration type, Logout access status, and Active lock status. The email must exactly match the trusted authenticated username; another email returns 403. Duplicate registration returns 400. Requires an accepted consumer token; the Pending-profile onboarding-token flow remains unimplemented.

##### Request DTO — `SelfRegistrationForm`

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
| `status` | `Account.Status` | `Approved` | Applied or Approved. |
| `registeredAt` | `Instant` | `2026-11-22T06:30:00Z` | UTC registration instant. |
| `approvedAt` | `Instant (nullable)` | `2026-11-22T07:00:00Z` | UTC approval instant; null until approved. |
| `lockStatus` | `AccountLockStatus.Status (nullable)` | `Active` | Active or Locked; null if no current lock record exists. |
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

## Current boundaries

Approval changes the local Consumer account only. Auth-profile activation and event publishing remain unimplemented; no Kafka events or remote account-status updates are sent.

Auth-server currently issues ordinary tokens only for Active profiles. Consequently, a newly registered Pending auth profile cannot yet obtain the consumer token needed for KYC/self-registration. A dedicated, restricted onboarding credential/flow must be designed before that end-to-end signup path works. The mobile endpoints have not been made public to bypass this gap.

The included resource-security rules enforce scopes. Fine-grained management role permissions remain a separate design decision. Access-history and lock-management operations are implemented by AccessHistoryService and LockStatusService.

Local PostgreSQL datasource defaults are provided in application.yml; environment overrides are described below. Tables are defined in `postgres/03-consumer-schema.sql`; Hibernate should validate rather than create/update them.

## Tests

From the project root:

```bash
mvn -pl consumer-service test
```

Tests use mocked HTTP/repositories and embedded H2 for Criteria queries and schema validation; they do not require Docker or exercise a live PostgreSQL/Kafka deployment.

## Configuration

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

The previous top-level `auth.*` properties are replaced by `app.auth.*`. Override the audience list with `AUTH_AUDIENCES` (comma-separated). Empty credentials/audiences and non-positive HTTP timeouts fail configuration validation. Changes require a restart.

| Configuration | Owner |
| --- | --- |
| Security filter chain and introspection HTTP client | `ResourceSecurityConfig`, using `AuthenticationProps` |
| Custom repository base class and method validation | `RepositoryConfig` and `ValidatedRepositoryFactoryBean` |
| DataSource, EntityManagerFactory, transaction manager | Spring Boot auto-configuration from `spring.datasource` / `spring.jpa` |
| KafkaTemplate, producer/consumer factories, listener container factory | Spring Boot auto-configuration from `spring.kafka` |

The PostgreSQL defaults use `wallet_demo`, schema `consumer`, and role `csmuser` with development password `csmpass`. Override with `CONSUMER_DB_URL`, `CONSUMER_DB_USERNAME`, and `CONSUMER_DB_PASSWORD`. Hibernate uses `ddl-auto: validate` and `open-in-view: false`; initialize tables using the scripts under `postgres` before starting the service. No migrations or automatic table creation are enabled.

Kafka defaults to `localhost:9092`, group `consumer-service`, String keys/values (including JSON text), record acknowledgment, and idempotent producer settings. Override using `KAFKA_BOOTSTRAP_SERVERS` and `KAFKA_CONSUMER_GROUP_ID`. No event listeners, publishers, topics, or business workflows are implemented by these settings. No extra datasource/Kafka bean configuration is needed unless customized behavior is introduced.

Host defaults assume services run on the host and infrastructure runs in Docker Compose. For container deployments, override localhost connection addresses appropriately.
