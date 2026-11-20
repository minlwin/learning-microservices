package com.jdc.auth.props;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@Data
@ConfigurationProperties(prefix = "app.mail")
public class MailConfigProps {

	private String from;
}
