package com.jdc.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

class OtpCodeServiceTest {

	@Test
	void sendsGeneratedCodeToRegistrationEmail() throws Exception {
		var sender = mock(JavaMailSender.class);
		when(sender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
		var service = new OtpCodeService(sender, "no-reply@wallet-demo.local", 3);

		var code = service.sendForRegistration("consumer@example.com");

		assertTrue(code.matches("[0-9]{6}"));
		var captor = ArgumentCaptor.forClass(MimeMessage.class);
		verify(sender).send(captor.capture());
		var message = captor.getValue();
		message.saveChanges();
		assertTrue(message.isMimeType("text/html"));
		assertEquals("consumer@example.com", ((InternetAddress) message.getAllRecipients()[0]).getAddress());
		assertEquals("no-reply@wallet-demo.local", ((InternetAddress) message.getFrom()[0]).getAddress());
		assertEquals("Confirm your registration", message.getSubject());
		assertTrue(((String) message.getContent()).contains(code));
		assertTrue(((String) message.getContent()).contains("3 minutes"));
	}

	@Test
	void propagatesMailFailureInsteadOfReturningCode() {
		var sender = mock(JavaMailSender.class);
		when(sender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
		var service = new OtpCodeService(sender, "no-reply@wallet-demo.local", 3);
		doThrow(new MailSendException("SMTP unavailable"))
				.when(sender).send(any(MimeMessage.class));

		assertThrows(MailSendException.class,
				() -> service.sendForRegistration("consumer@example.com"));
	}
}
