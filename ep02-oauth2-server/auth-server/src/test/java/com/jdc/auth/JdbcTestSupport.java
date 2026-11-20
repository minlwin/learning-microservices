package com.jdc.auth;

import java.util.UUID;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

public final class JdbcTestSupport {
	private JdbcTestSupport() {
	}

	public static JdbcTemplate createDatabase() {
		var dataSource = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
		var scripts = new ResourceDatabasePopulator();
		scripts.addScript(new ClassPathResource("org/springframework/security/oauth2/server/authorization/client/oauth2-registered-client-schema.sql"));
		scripts.addScript(new ClassPathResource("org/springframework/security/oauth2/server/authorization/oauth2-authorization-schema.sql"));
		scripts.execute(dataSource);
		return new JdbcTemplate(dataSource);
	}
}
