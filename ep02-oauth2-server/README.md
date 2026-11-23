# Learning Authorization Server

A Java and Spring Boot learning project for an OAuth2-based, multi-service wallet backend. It demonstrates authentication and token management, separate Consumer and Management APIs, service-owned data schemas, and asynchronous communication with Kafka.

The project is a Maven reactor with three applications. Each service README contains its detailed setup, configuration, database tables, API reference, and implementation notes.

## Services

| Service | Purpose | Default URL | Details |
| --- | --- | --- | --- |
| Auth Server | User registration and activation, authentication, token issuance, internal token introspection, and PIN lock coordination. | `http://localhost:8080` | [Auth Server README](auth-server/README.md) |
| Consumer Service | Consumer account registration and approval, Consumer profile APIs, access history, and PIN lock history. | `http://localhost:8081` | [Consumer Service README](consumer-service/README.md) |
| Management Service | Management accounts, PIN lock policies, configured client systems, and Management PIN lock history. | `http://localhost:8082` | [Management Service README](management-service/README.md) |

The services keep their data in separate PostgreSQL schemas. They communicate over HTTP for authentication and internal operations, and use Kafka events for account lifecycle and PIN lock updates. Auth Server owns identity, credentials, and token state; Consumer and Management Services own their respective domain records.

## Local infrastructure

Docker Compose provides PostgreSQL, Kafka, and Mailpit for local development.

| Component | Address | Use |
| --- | --- | --- |
| PostgreSQL | `localhost:5432` | Service database schemas and roles. |
| Kafka | `localhost:9092` | Inter-service events. |
| Mailpit SMTP | `localhost:1025` | Development email delivery. |
| Mailpit web UI | [http://localhost:8025](http://localhost:8025) | View OTP and invitation emails. |

## Start locally

Requirements: Java 25, Maven, and Docker Compose. From the project root, start the infrastructure:

```bash
export POSTGRES_PASSWORD=postgres
docker compose up -d
```

Run each application in a separate terminal:

```bash
mvn -pl auth-server spring-boot:run
```

```bash
mvn -pl consumer-service spring-boot:run
```

```bash
mvn -pl management-service spring-boot:run
```

Build the full Maven reactor with `mvn clean package`. Each service can also be started individually; see its README for prerequisites, configuration, and service-specific commands.

Compose initializes the PostgreSQL schemas only when the database volume is empty. If you change the development DDL and want to recreate the local database, `docker compose down -v` removes the PostgreSQL and Kafka volumes, then `docker compose up -d` initializes them again.

## Registration and account lifecycle

- Consumer self-registration begins in Auth Server, where the user verifies an email address and initializes a password. The Consumer Service stores the registration for Management review; approval activates the Consumer profile.
- A Management user can create a Consumer account. After Management approves it, Auth Server sends an invitation so the Consumer can verify the email and initialize a password.
- Management accounts are created by an administrator and activated by the invited user. They do not require a separate approval step.

For endpoint-level details, request and response formats, and flow notes, use the linked service READMEs.

## Additional guides

- [Authentication endpoint](readme/authentication-endpoint.md)
- [Token endpoint](readme/token-endpoint.md)
- [Logout](readme/logout.md)
- [Administrator bootstrap](readme/administrator-bootstrap.md)
- [GitHub Actions](readme/github-actions.md)
