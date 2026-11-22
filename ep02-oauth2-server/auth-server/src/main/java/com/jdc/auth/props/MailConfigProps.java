package com.jdc.auth.props;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;

@Data
@Validated
@ConfigurationProperties(prefix = "app.mail")
public class MailConfigProps {

	@NotBlank
	@Email
	private String from;
}
