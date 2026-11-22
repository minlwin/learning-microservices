package com.jdc.auth.model;

import java.nio.file.Files;
import java.nio.file.Path;
import com.jdc.auth.model.entity.Activation;
import java.util.Map;
import java.util.HashSet;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

class AuthSchemaValidationTest {
	@Test
	void validatesSqlAgainstAuthEntityMappings() throws Exception {
		var dataSource = new DriverManagerDataSource("jdbc:h2:mem:auth_schema;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
		var jdbc = new JdbcTemplate(dataSource);
		jdbc.execute("CREATE SCHEMA auth");
		var script = Path.of("postgres/02-auth-schema.sql");
		if (!Files.exists(script)) {
			script = Path.of("../postgres/02-auth-schema.sql");
		}
		var sql = Files.readString(script);
		sql = sql.replace("SET LOCAL ROLE authuser;", "");
		jdbc.execute(sql);
		var factory = new LocalContainerEntityManagerFactoryBean();
		factory.setDataSource(dataSource);
		factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
		factory.setPackagesToScan("com.jdc.auth.model.entity");
		factory.setJpaPropertyMap(Map.of(
				"hibernate.hbm2ddl.auto", "validate",
				"hibernate.default_schema", "auth",
				"hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect",
				"hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl"));
		try {
			factory.afterPropertiesSet();
			// Hibernate validation detects missing tables/columns, but not obsolete tables.
			var expectedTables = new HashSet<String>();
			for (var entity : factory.getObject().getMetamodel().getEntities()) {
				var table = entity.getJavaType().getSimpleName().replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
				expectedTables.add(table);
			}
			// These two tables belong to Spring Security JDBC repositories, not JPA entities.
			expectedTables.add("oauth2_registered_client");
			expectedTables.add("oauth2_authorization");
			var actualTables = jdbc.queryForList(
					"SELECT lower(table_name) FROM information_schema.tables WHERE lower(table_schema) = 'auth' AND table_type = 'BASE TABLE'", String.class);
			assertEquals(expectedTables, new HashSet<>(actualTables));
			try (var entityManager = factory.getObject().createEntityManager()) {
				entityManager.getTransaction().begin();
				var activation = new Activation();
				activation.setEmail("invited@example.com");
				entityManager.persist(activation);
				entityManager.getTransaction().commit();
			}

		} finally {
			factory.destroy();
		}
	}
}
