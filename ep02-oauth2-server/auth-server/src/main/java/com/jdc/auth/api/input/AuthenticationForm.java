package com.jdc.auth.api.input;

import jakarta.validation.constraints.NotBlank;

public record AuthenticationForm(
		@NotBlank(message = "Enter the client ID.")
		String clientId,
		@NotBlank(message = "Enter email address")
		String username,
		@NotBlank(message = "Enter password")
		String password) {

}
