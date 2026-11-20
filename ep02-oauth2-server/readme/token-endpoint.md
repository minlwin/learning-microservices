# Allowed endpoints and token requests

The server exposes application routes under `/api/**` and `POST /oauth2/token`.
Onboarding and authentication routes are public. `POST /api/logout` requires a
valid access token. `/api/internal/tokens/introspect` requires separate
resource-server credentials; it does not accept user access tokens as client
credentials. Add authorization rules for future protected application features.

All other paths and methods are denied with 403, including `/login`, `/logout`,
`/oauth2/authorize`, discovery metadata, JWK sets, introspection, revocation,
device authorization, user info, and client registration. There is no login
page or redirect. The authorization-server filter chain only matches
`POST /oauth2/token`; its other protocol filters cannot serve requests.

The same token endpoint handles access-token issuance and refresh. No separate
refresh endpoint is needed.

## Demo client authentication

| Client | System | Default secret |
|---|---|---|
| consumer-demo | Consumer | consumer-demo-secret |
| management-demo | Management | management-demo-secret |

Override secrets with `CONSUMER_CLIENT_SECRET` and `MANAGEMENT_CLIENT_SECRET`.
These confidential clients are for Postman or a trusted server backend.
Do not embed their secrets in a mobile app or browser JavaScript. Public-client
support needs separate implementation; it is not enabled by this configuration.

## Exchange a login ticket

First obtain a ticket from `/api/auth` using the same client ID.

```bash
curl -u consumer-demo:consumer-demo-secret \
  -X POST http://localhost:8080/oauth2/token \
  --data-urlencode 'grant_type=urn:jdc:params:oauth:grant-type:login-ticket' \
  --data-urlencode 'login_ticket=YOUR_LOGIN_TICKET'
```

The response includes `access_token`, `refresh_token`, `token_type`, `expires_in`,
and `scope`. The custom grant uses only the scope configured for the client;
supplying a `scope` parameter in this exchange is rejected. It issues no ID token.
Access tokens last 10 minutes; refresh tokens last eight hours and rotate.

## Refresh

```bash
curl -u consumer-demo:consumer-demo-secret \
  -X POST http://localhost:8080/oauth2/token \
  --data-urlencode 'grant_type=refresh_token' \
  --data-urlencode 'refresh_token=YOUR_REFRESH_TOKEN'
```

Replace the old refresh token with the newly returned value. Account status and
system are checked again before generating the refreshed access token.
Logout increments the profile's token version. Old access tokens, refresh tokens,
and outstanding login tickets then fail validation. Both resource services check
current validity on every request; see `logout.md`.

Token failures use OAuth's `error` JSON format, generated centrally by
`ExceptionHandlers.oauth2Failure`. Other API errors use ProblemDetail JSON.

## Validation and learning-environment limits

Spring's discovery, JWK, introspection, revocation, and logout endpoints remain
blocked. Resource servers now use the authenticated custom endpoint
`/api/internal/tokens/introspect` to validate the exact access token against
server authorization state and the profile's current token version. They also
check issuer and audience. They do not rely on offline JWT signature checks.

This learning configuration generates an RSA signing key at startup and keeps
OAuth authorizations and refresh state in memory. A restart loses that state
and therefore rejects all previously issued access and refresh tokens during
live validation. Login tickets and profile token versions remain in PostgreSQL.
Durable authorization storage and managed signing keys are needed before
production/multiple replicas. HTTPS, public-client support, login rate limits,
and any required MFA must be addressed before deployment. No Kafka publishing
is added here.
