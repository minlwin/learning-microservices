# Management service

Default HTTP port: `8082`.

## Configuration

Custom security settings are bound to validated `AuthenticationProps` under `app.auth`:

```yaml
app:
  auth:
    introspection-uri: ${AUTH_INTROSPECTION_URI:http://localhost:8080/api/internal/tokens/introspect}
    issuer: ${AUTH_ISSUER:http://localhost:8080}
    resource-id: management-service
    resource-secret: ${MANAGEMENT_RESOURCE_SECRET:management-resource-secret}
    audiences: [management.api]
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

The PostgreSQL defaults use `wallet_demo`, schema `management`, and role `mnguser` with development password `mngpass`. Override with `MANAGEMENT_DB_URL`, `MANAGEMENT_DB_USERNAME`, and `MANAGEMENT_DB_PASSWORD`. Hibernate uses `ddl-auto: validate` and `open-in-view: false`; initialize tables using the scripts under `postgres` before starting the service. No migrations or automatic table creation are enabled.

Kafka defaults to `localhost:9092`, group `management-service`, String keys/values (including JSON text), record acknowledgment, and idempotent producer settings. Override using `KAFKA_BOOTSTRAP_SERVERS` and `KAFKA_CONSUMER_GROUP_ID`. No event listeners, publishers, topics, or business workflows are implemented by these settings. No extra datasource/Kafka bean configuration is needed unless customized behavior is introduced.

Host defaults assume services run on the host and infrastructure runs in Docker Compose. For container deployments, override localhost connection addresses appropriately.
