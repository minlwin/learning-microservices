package com.jdc.auth.service;

import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import com.jdc.auth.props.MailConfigProps;
import com.jdc.auth.props.OtpCodeProps;
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
			MailConfigProps mailProperties, OtpCodeProps otpProperties) {
		this.mailSender = mailSender;
		this.from = mailProperties.getFrom();
		this.registrationExpiration = otpProperties.getExpiration().getRegistration();
	}

	public String sendForRegistration(String email) {
		return send(email, OtpPurpose.REGISTRATION, registrationExpiration);
	}

	public String sendForActivation(String email, int expirationMinutes) {
		return send(email, OtpPurpose.ACTIVATION, expirationMinutes);
	}

	public void sendInvitation(String system, String email, String invitationUrl) {
		var consumer = "Consumer".equals(system);
		if (!consumer && !"Management".equals(system)) {
			throw new IllegalArgumentException("Unsupported invitation system.");
		}
		var accountName = consumer ? "Consumer" : "Management";
		var action = consumer ? "registration" : "activation";
		var message = mailSender.createMimeMessage();
		try {
			var helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
			helper.setFrom(from);
			helper.setTo(email);
			helper.setSubject("Complete your %s account %s".formatted(accountName, action));
			helper.setText("""
				<!DOCTYPE html>
				<html lang="en">
				<body>
				  <h1>Complete your %s account %s</h1>
				  <p>A %s account has been created for you. Use the link below to verify your email and set your password.</p>
				  <p><a href="%s">Continue %s</a></p>
				  <p>If you did not expect this message, please contact support.</p>
				</body>
				</html>
				""".formatted(accountName, action, accountName, invitationUrl, action), true);
		} catch (MessagingException exception) {
			throw new MailPreparationException("Could not prepare %s invitation email.".formatted(system), exception);
		}
		mailSender.send(message);
	}

	private String send(String email, OtpPurpose purpose, int expirationMinutes) {
		var code = generate();
		var message = mailSender.createMimeMessage();
		try {
			var helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
			helper.setFrom(from);
			helper.setTo(email);
			helper.setSubject(purpose.subject);
			helper.setText("""
				<!DOCTYPE html>
				<html lang="en">
				<head>
				  <meta charset="UTF-8">
				  <title>%s</title>
				</head>
				<body style="margin: 0; padding: 24px; background-color: #f3f4f6; font-family: Arial, sans-serif; color: #111827;">
				  <div style="max-width: 480px; margin: 0 auto; padding: 32px; background-color: #ffffff; border-radius: 8px;">
				    <h1 style="font-size: 24px;">%s</h1>
				    <p>Use the verification code below to continue your %s.</p>
				    <p style="font-size: 32px; font-weight: bold; letter-spacing: 6px;">%s</p>
				    <p>This code expires in <strong>%d minutes</strong>. Do not share it with anyone.</p>
				    <p style="color: #6b7280;">If you did not request this, you can ignore this email.</p>
				  </div>
				</body>
				</html>
				""".formatted(purpose.subject, purpose.subject, purpose.action, code, expirationMinutes), true);
		} catch (MessagingException exception) {
			throw new MailPreparationException("Could not prepare OTP email", exception);
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

	private enum OtpPurpose {
		REGISTRATION("Confirm your registration", "registration"),
		ACTIVATION("Confirm your account activation", "account activation");

		private final String subject;
		private final String action;

		OtpPurpose(String subject, String action) {
			this.subject = subject;
			this.action = action;
		}
	}
}
