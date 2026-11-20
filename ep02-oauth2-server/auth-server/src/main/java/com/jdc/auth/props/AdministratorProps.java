package com.jdc.auth.props;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@Data
@ConfigurationProperties(prefix = "app.administrator")
public class AdministratorProps {

	private String system = "Management";
	private String role = "Administrator";
	private String username;
	private String password;
}
