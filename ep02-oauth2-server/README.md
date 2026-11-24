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

Requirements: Docker with Docker Compose. From the project root, build and start the complete local environment, including all three Spring Boot services:

```bash
docker compose up --build -d
docker compose ps
```

The applications are available at `http://localhost:8080` (Auth Server), `http://localhost:8081` (Consumer Service), and `http://localhost:8082` (Management Service). PostgreSQL, Kafka, and Mailpit are also available on the local ports listed above. Compose uses container DNS names for service-to-service traffic and keeps published ports bound to `127.0.0.1`.

For the Java hot-reload development workflow, start only the infrastructure containers and run the service applications from Maven. Do not start a service both ways at the same time because they use the same ports:

```bash
docker compose stop auth-server consumer-service management-service
docker compose up -d postgres kafka mailpit
mvn -pl auth-server spring-boot:run
```

Open separate terminals for `mvn -pl consumer-service spring-boot:run` and `mvn -pl management-service spring-boot:run` as needed. This host-run mode uses the `localhost` configuration defaults. For a full Maven build, run `mvn clean package` from the project root.

Stop the containers with `docker compose down`; data volumes are preserved. Compose initializes PostgreSQL schemas only when the database volume is empty. If you change the development DDL and want to recreate local data, `docker compose down -v` removes the PostgreSQL, Kafka, and Auth Server signing-key volumes before the next startup. The signing-key volume keeps tokens verifiable across Auth Server container restarts.

## Registration and account lifecycle

- Consumer self-registration begins in Auth Server, where the user verifies an email address and initializes a password. The Consumer Service stores the registration for Management review; approval activates the Consumer profile.
- A Management user can create a Consumer account. After Management approves it, Auth Server sends an invitation so the Consumer can verify the email and initialize a password.
- Management accounts are created by an administrator and activated by the invited user. They do not require a separate approval step.

For endpoint-level details, request and response formats, and flow notes, use the linked service READMEs.

## Additional guides

- [Postman API guide](readme/postman-api-guide.md)
- [Authentication endpoint](readme/authentication-endpoint.md)
- [Token endpoint](readme/token-endpoint.md)
- [Logout](readme/logout.md)
- [Administrator bootstrap](readme/administrator-bootstrap.md)
- [GitHub Actions](readme/github-actions.md)
