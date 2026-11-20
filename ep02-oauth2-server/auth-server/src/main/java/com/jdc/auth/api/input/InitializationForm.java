package com.jdc.auth.api.input;

import jakarta.validation.constraints.NotBlank;

public record InitializationForm(
		@NotBlank(message = "Please enter your password.") String password) {

}
