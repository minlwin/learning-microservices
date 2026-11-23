package com.jdc.auth.api.input;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InitializationForm(
		@NotBlank(message = "Please enter your password.")
		@Size(min = 12, max = 72, message = "Password must contain between 12 and 72 characters.")
		String password) {

}
