package com.jdc.auth.api.input;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OtpForm(
		@NotBlank(message = "Please enter your verification code.") @Pattern(regexp = "[0-9]{6}", message = "Enter a six-digit verification code.") String otpCode) {

}
