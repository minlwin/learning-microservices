package com.jdc.management.api.input;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AccountRegistrationForm(
        @NotBlank(message = "Name is required.")
        @Size(max = 255, message = "Name must not exceed 255 characters.") String name,
        @NotBlank(message = "Phone number is required.")
        @Size(max = 255, message = "Phone number must not exceed 255 characters.") String phone,
        @NotBlank(message = "Email address is required.")
        @Email(message = "Enter a valid email address.")
        @Size(max = 255, message = "Email address must not exceed 255 characters.") String email) {
}
