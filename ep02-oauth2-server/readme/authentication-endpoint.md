# Authentication endpoint

[Back to project overview](../README.md)

POST `/api/auth` with `Content-Type: application/json`:

```json
{
  "clientId": "consumer-demo",
  "username": "consumer@example.com",
  "password": "your-password"
}
```

The configured client ID selects the profile system. `consumer-demo` selects
Consumer; `management-demo` selects Management. These mappings are configured
under `app.auth.clients` in application.yml. Supplying a client ID identifies
the application context; it is not proof of the application's identity.

Only profiles with a matching password and Active status receive a ticket:

```json
{
  "loginTicket": "opaque-random-value",
  "expiresIn": 60
}
```

This is a custom login ticket, not an OpenID Connect ID token. Its SHA-256 hash
is stored in `auth.login_ticket`, bound to the profile and client ID. Responses
are marked `Cache-Control: no-store`. Missing input returns 400; invalid
credentials, unknown clients/users, and inactive profiles return the same
generic 401 response.

`LoginTicketService.consume` checks the client binding, expiry, consumption
state, profile system, and profile status. A database write lock protects
consumption against concurrent replay. The custom OAuth token grant validates
the client before calling it. See `token-endpoint.md` for exchange and refresh
requests.

The fresh-database script `postgres/02-auth-schema.sql` includes the new ticket
table. Recreate your development database using your existing reset workflow
before running this version. No migrations are included.

Login OTP/MFA, login rate limiting, and cleanup of expired/consumed ticket rows
remain future work. Use HTTPS outside local development and never log passwords
or raw login tickets. Configure client context and proof-of-possession rules
when implementing the token exchange.
