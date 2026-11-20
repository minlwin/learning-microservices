package com.jdc.auth.service;

import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import jakarta.mail.MessagingException;

@Service
public class OtpCodeService {

	private final SecureRandom random = new SecureRandom();
	private final JavaMailSender mailSender;
	private final String from;
	private final int registrationExpiration;

	public OtpCodeService(JavaMailSender mailSender,
			@Value("${app.mail.from}") String from,
			@Value("${app.otp.expiration.registration}") int registrationExpiration) {
		this.mailSender = mailSender;
		this.from = from;
		this.registrationExpiration = registrationExpiration;
	}

	public String sendForRegistration(String email) {
		return send(email, "registration", registrationExpiration);
	}

	public String sendForActivation(String email, int expirationMinutes) {
		return send(email, "activation", expirationMinutes);
	}

	private String send(String email, String purpose, int expirationMinutes) {
		var code = generate();
		var message = mailSender.createMimeMessage();
		try {
			var helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
			helper.setFrom(from);
			helper.setTo(email);
			helper.setSubject("Confirm your " + purpose);
			helper.setText("""
				<!DOCTYPE html>
				<html lang="en">
				<head>
				  <meta charset="UTF-8">
				  <title>Confirm your registration</title>
				</head>
				<body style="margin: 0; padding: 24px; background-color: #f3f4f6; font-family: Arial, sans-serif; color: #111827;">
				  <div style="max-width: 480px; margin: 0 auto; padding: 32px; background-color: #ffffff; border-radius: 8px;">
				    <h1 style="font-size: 24px;">Confirm your registration</h1>
				    <p>Use the verification code below to continue your registration.</p>
				    <p style="font-size: 32px; font-weight: bold; letter-spacing: 6px;">%s</p>
				    <p>This code expires in <strong>%d minutes</strong>. Do not share it with anyone.</p>
				    <p style="color: #6b7280;">If you did not request this registration, you can ignore this email.</p>
				  </div>
				</body>
				</html>
				""".replace("registration", purpose).formatted(code, expirationMinutes), true);
		} catch (MessagingException e) {
			throw new MailPreparationException("Could not prepare OTP email", e);
		}
		if(TransactionSynchronizationManager.isActualTransactionActive()
				&& TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					mailSender.send(message);
				}
			});
		} else {
			mailSender.send(message);
		}

		return code;
	}
	
	private String generate() {
		return String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
	}
}
