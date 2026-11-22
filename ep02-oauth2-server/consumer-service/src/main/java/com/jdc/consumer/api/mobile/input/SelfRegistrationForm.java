package com.jdc.consumer.api.mobile.input;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record SelfRegistrationForm(
		@Size(max = 255, message = "Use at most 255 characters.")
		@NotBlank(message = "Enter customer name")
		String name,
		@Email(message = "Enter a valid email address.")
		@Size(max = 255, message = "Use at most 255 characters.")
		@NotBlank(message = "Enter email address")
		String email,
		@Size(max = 255, message = "Use at most 255 characters.")
		@NotBlank(message = "Enter phone number")
		String phone,
		@NotNull(message = "Enter date of birth")
		@Past(message = "Date of birth must be in the past.")
		LocalDate dob,
		@Size(max = 255, message = "Use at most 255 characters.")
		@NotBlank(message = "Enter address")
		String address) {

}
