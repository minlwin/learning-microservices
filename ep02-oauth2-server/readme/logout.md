# Logout from all devices for one profile

```bash
curl -X POST http://localhost:8080/api/logout \
  -H 'Authorization: Bearer YOUR_ACCESS_TOKEN'
```

Success returns 200 with no body, consistent with the existing void endpoints.
The client should then clear its stored access and refresh tokens. This endpoint
requires an active access token; a refresh token or user-supplied profile ID is
not accepted for authentication. Reusing an old access token after logout returns
401. A second logout with the same revoked token also returns 401.

## Scope and race handling

Logout atomically increments `Profile.tokenVersion`. Every previously issued
access token, refresh authorization, and unused login ticket carries the old
version. They cannot be upgraded to the new version by refreshing or exchanging
an old ticket. Login credentials remain unchanged; a fresh login receives the
new version. Invalidated records may remain stored but cannot be used.

The profile comes from the authenticated access token. Logging out Consumer
logs out all devices for that Consumer profile. It does not log out a Management
profile sharing the same Identity. A token-version conditional update ensures
concurrent logout requests cannot overwrite a newer version.

Logout cannot cancel a business operation that has already passed its security
check. Subsequent requests validated after the logout transaction commits are
rejected. Other resource services that use offline JWT validation must adopt the
same live validation mechanism to observe immediate revocation.

## Live validation

Both consumer-service and management-service call the auth server on every
protected request, without a validity cache. The exact token must exist in the
OAuth authorization store, be active/unexpired, and match an Active profile's
current token version. Signature decoding alone cannot bypass this check.

The internal endpoint is:

`POST /api/internal/tokens/introspect`

It uses HTTP Basic authentication with dedicated resource-server credentials,
not OAuth client secrets or user passwords. The request is form encoded with a
`token` parameter. The response is an introspection-style JSON object containing
`active`, subject, issuer, audience, scopes, and token version for active tokens;
invalid tokens return only `{"active": false}`. Responses must not be cached.
Each resource credential is restricted to its configured audiences.

Configure the same secrets on the auth server and corresponding resource server:

| Resource | Secret environment variable | Allowed audiences |
|---|---|---|
| consumer-service | CONSUMER_RESOURCE_SECRET | consumer.api, management.api |
| management-service | MANAGEMENT_RESOURCE_SECRET | management.api |

Local demo defaults are consumer-resource-secret and management-resource-secret.
Set secure values and use HTTPS for deployment. Keep the internal endpoint on
trusted networks where possible. Spring's `/oauth2/introspect` stays blocked.

Resource-server settings:

- AUTH_INTROSPECTION_URI defaults to http://localhost:8080/api/internal/tokens/introspect.
- AUTH_ISSUER defaults to http://localhost:8080 and must match the token issuer.
- Consumer service uses port 8081; management service uses port 8082. Override with SERVER_PORT.
- Network connect/read timeouts are three seconds. Validation fails closed if the auth server is unavailable.
- Consumer `/api/self/**` routes require consumer.api; `/api/management/**` routes require management.api.
- Management `/api/**` routes require management.api. Other resource-server routes are denied.

## Database and deployment

`postgres/02-auth-schema.sql` now includes token_version BIGINT columns on
profile and login_ticket. Recreate the development database with your chosen
Docker reset workflow before running; no migration files were added.

OAuth authorizations are still in memory. Use shared durable authorization
storage before deploying multiple auth replicas. The database version check is
shared, but one replica cannot introspect tokens issued only in another
replica's memory. No Kafka events were implemented.

Tests cover token exchange, multiple devices, logout, old refresh rejection,
old ticket rejection, separate-profile isolation, fresh login, protected internal
credentials, audience restrictions, and resource-server live-check behavior.
The local checks use mocked repositories/HTTP transports, not a live PostgreSQL
instance or a deployed network environment.
