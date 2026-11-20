package com.jdc.auth.api.input;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RegistrationForm(
		@NotBlank(message = "Please enter your email address.")
		@Email(message = "Enter a valid email address")
		String email) {

}
