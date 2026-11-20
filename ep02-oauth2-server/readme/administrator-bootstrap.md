# Initial administrator

`AdministratorInitializer` runs during application startup inside a transaction.
If `auth.profile` has no rows, it creates an Identity and an Administrator
profile using `AdministratorProps`. An existing Identity with the configured
username is reused if it has no profiles yet.

The profile has `system = Management`, `role = Administrator`, `status = Active`, and an encoded
password. It requires neither email verification nor activation. If any profile
already exists, initialization is skipped and no password is reset. Blank
bootstrap credentials cause the first startup to fail without creating an account.

Configure the first account through `ADMINISTRATOR_USERNAME` and
`ADMINISTRATOR_PASSWORD`, corresponding to the existing `app.administrator`
settings. Changing these settings after users exist does not change stored
credentials. The current initializer targets the single-instance learning setup;
coordinate first-time initialization before deploying multiple auth replicas.

Use `clientId: management-demo` with the configured administrator username and
password at `/api/auth`. The management client authenticates the Management profile directly. There is
no Administrator system or fallback lookup. Consumer clients cannot use
Management profiles, including those with the Administrator role.

The management access token has audience/scope `management.api`, system
`Management`, and `roles: ["Administrator"]`. Live introspection preserves
the role, and both resource services map it to `ROLE_Administrator`. Protected
management APIs can therefore use `hasRole("Administrator")` where role-based
authorization is required. Existing management API scope rules also admit this
token. Refresh and logout retain the existing token-version checks.

The fresh schema in `postgres/02-auth-schema.sql` includes a required ordinal
role column: Consumer=0, Management=1, Administrator=2. System is Consumer=0 or
Management=1. Consumer profiles must have the Consumer role; Management profiles
can have Management or Administrator roles. Recreate the development database
with the latest script before running. No migrations or Kafka events are added.
