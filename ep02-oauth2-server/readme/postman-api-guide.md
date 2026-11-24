# Postman API guide

[Back to project overview](../README.md)

This guide explains how to use the local Postman collection to exercise the Auth Server, Consumer Service, and Management Service APIs. For endpoint request and response details, see the [Auth Server](../auth-server/README.md), [Consumer Service](../consumer-service/README.md), and [Management Service](../management-service/README.md) READMEs.

## Prerequisites

From the project root, build and start all services and local infrastructure:

```bash
docker compose up --build -d
docker compose ps
```

The default service URLs are:

| Service | URL |
| --- | --- |
| Auth Server | `http://localhost:8080` |
| Consumer Service | `http://localhost:8081` |
| Management Service | `http://localhost:8082` |

Open [Mailpit](http://localhost:8025) to read development OTP and invitation emails. For the Maven host-run workflow, see the [root README](../README.md#start-locally); stop the corresponding service container first to avoid a port conflict.

## Open the collection

The project Postman files are under [`postman/collections/Learning Authorization Server`](../postman/collections/Learning%20Authorization%20Server), and the environment is [`postman/environments/Local.environment.yaml`](../postman/environments/Local.environment.yaml). The repository's `.postman/resources.yaml` registers resources from `postman/` in Postman's Local View when the project is open in Postman.

In Postman, select the **Local** environment before sending a request. It contains the local base URLs, OAuth client IDs, resource IDs, and documented development credentials. If your application configuration differs, update the matching environment values. The included secrets are for local learning only; do not use them outside this development setup.

## Environment variables

Common values you may need to edit or provide:

| Variable | Use |
| --- | --- |
| `AUTH_SERVER`, `CONSUMER_SERVICE`, `MANAGEMENT_SERVICE` | Base URLs for the three applications. |
| `ADMIN_USERNAME`, `ADMIN_PASSWORD` | Initial Auth Server administrator credentials. The documented local password is `ChangeThisLocalAdminPassword1`. |
| `MANAGEMENT_EMAIL`, `MANAGEMENT_PASSWORD` | Credentials for a Management user created and activated during the flow. |
| `CONSUMER_EMAIL`, `CONSUMER_PASSWORD` | Consumer identity used by registration and login requests. |
| `OTP_CODE` | Six-digit code copied from the latest email in Mailpit. |
| `CONSUMER_INVITATION_ID`, `MANAGEMENT_ACTIVATION_ID` | IDs from a Consumer invitation link or Management account, respectively. |
| `PIN_LOCK_SYSTEM` | System path value for creating or updating PIN lock settings, such as `Consumer`. |

Successful requests save returned tickets and access tokens. Search requests also save matching account IDs to the environment when a result has the configured email. You can inspect or clear these values in Postman's environment editor.

## Authenticate as the initial administrator

Run these requests in the **Authentication** folder:

1. **Authenticate Management Admin** — checks the configured admin username and password and saves `MANAGEMENT_LOGIN_TICKET`.
2. **Exchange Management Login Ticket** — exchanges that one-time ticket for a Management access token and refresh token.
3. Use the access token for Management Service and Consumer Service management requests that require a management bearer token.

The login ticket is one-time and expires. If its exchange fails or it has expired, repeat the authentication request first. Refresh requests rotate the refresh token and save the newly returned values. **Log Out Management Profile** invalidates tokens and tickets for that profile, so send it after other calls that need this session.

## Register and activate a Management user

Use this flow to exercise the management account invitation process:

1. With the administrator access token, run **Create Management Account** in **Management Service**.
2. Run **Search Management Accounts** to find the new account. The request saves its ID as `MANAGEMENT_ACCOUNT_ID` and `MANAGEMENT_ACTIVATION_ID` when `MANAGEMENT_EMAIL` matches.
3. In **Management Activation**, run **Request Activation OTP**. Open the message in Mailpit and copy the latest six-digit code into `OTP_CODE`.
4. Run **Validate Activation OTP**, then **Initialize Management Password**. Set `MANAGEMENT_PASSWORD` to the password you want the new user to use.
5. Run **Authenticate Management User** in **Authentication**, followed by **Exchange Management Login Ticket**. These requests use `MANAGEMENT_EMAIL` and `MANAGEMENT_PASSWORD` to obtain the new user's Management access token.

Management users activate their own accounts. This flow does not include a separate approval request.

## Consumer self-registration

The **Self Registration** and **Consumer Service** folders cover the self-registration path:

1. Run **Start Consumer Self Registration** and read the OTP from Mailpit.
2. Set `OTP_CODE`, then run **Validate Registration OTP**.
3. Set `CONSUMER_PASSWORD` and run **Initialize Consumer Password**. This saves the one-time login ticket.
4. Run **Exchange Consumer Onboarding Ticket** in **Authentication**. It saves the limited onboarding access token.
5. Run **Submit Self Registration Details** in **Consumer Service** to create the Applied account.
6. With a Management access token, search for the account, then run **Approve Consumer** or **Reject Consumer**. Approval is processed asynchronously by Auth Server.
7. After approval has propagated, set `CONSUMER_EMAIL` and `CONSUMER_PASSWORD`, then run **Authenticate Consumer** and **Exchange Consumer Login Ticket** to obtain the regular Consumer access token. Use it with **Get My Consumer Profile**.

The onboarding token is for the initial registration call. After approval, authenticate again to obtain a normal Consumer token.

## Management-created Consumer registration

1. With a Management access token, run **Register Consumer For Customer** in **Consumer Service**.
2. Find the new account using **Search Consumer Accounts**, then run **Approve Consumer**. Auth Server creates an invitation after processing the approval event.
3. Open the invitation email in Mailpit and copy the registration ID from its link into `CONSUMER_INVITATION_ID`.
4. Run **Request Invitation OTP** in **Self Registration**, copy the OTP from Mailpit into `OTP_CODE`, and run **Validate Registration OTP**.
5. Set `CONSUMER_PASSWORD` and run **Initialize Consumer Password**. This invitation flow activates the Consumer profile directly; it does not need an onboarding token or a second Consumer Service registration.
6. Authenticate the Consumer and exchange the login ticket using the **Authentication** requests.

## Other API requests

- **Consumer Service** contains account search, registration, approval/rejection, access and PIN-lock history searches, permanent unlock, mobile registration, and profile lookup.
- **Management Service** contains account search/create/update, Management PIN-lock history and unlock, configured-system queries, and PIN-lock setting list/create/update.
- **Internal APIs** contains token introspection, email checks, and Auth Server's internal PIN unlock call. These use resource-server Basic credentials rather than a user's bearer token.
- **Authentication** contains login-ticket exchange, refresh, and logout requests.

Search requests include sample query values. Adjust or remove filters to match the data you want to inspect.

## Running requests safely

Requests are stateful and are not intended to be sent as one uninterrupted collection run. OTP verification requires reading email; Consumer approval and event processing require separate steps; and create, approve, reject, unlock, and setting requests change data. Run the relevant requests one at a time and use disposable local data when exploring. A repeated state transition may correctly return a client error because the account is no longer in the required state.

Each request includes a basic success assertion. Use the service READMEs linked above for the full response shape, validation rules, and documented error responses.
