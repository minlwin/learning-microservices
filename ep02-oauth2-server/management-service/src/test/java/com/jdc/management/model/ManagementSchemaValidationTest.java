package com.jdc.management.model;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.HashSet;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

class ManagementSchemaValidationTest {
	@Test
	void validatesSqlAgainstManagementEntityMappings() throws Exception {
		var dataSource = new DriverManagerDataSource("jdbc:h2:mem:management_schema;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
		var jdbc = new JdbcTemplate(dataSource);
		jdbc.execute("CREATE SCHEMA management");
		var script = Path.of("postgres/04-management-schema.sql");
		if (!Files.exists(script)) {
			script = Path.of("../postgres/04-management-schema.sql");
		}
		var sql = Files.readString(script);
		sql = sql.replace("SET LOCAL ROLE mnguser;", "");
		jdbc.execute(sql);
		var factory = new LocalContainerEntityManagerFactoryBean();
		factory.setDataSource(dataSource);
		factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
		factory.setPackagesToScan("com.jdc.management.model.entity");
		factory.setJpaPropertyMap(Map.of(
				"hibernate.hbm2ddl.auto", "validate",
				"hibernate.default_schema", "management",
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
			var actualTables = jdbc.queryForList(
					"SELECT lower(table_name) FROM information_schema.tables WHERE lower(table_schema) = 'management' AND table_type = 'BASE TABLE'", String.class);
			assertEquals(expectedTables, new HashSet<>(actualTables));

		} finally {
			factory.destroy();
		}
	}
}
