package com.jdc.auth.api.input;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailCheckForm(
		@NotBlank(message = "Enter the system ID.")
		@Size(max = 255) String system,
		@NotBlank(message = "Enter the email address.")
		@Email(message = "Enter a valid email address.")
		@Size(max = 255) String email) {
}
