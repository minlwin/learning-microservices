package com.jdc.consumer.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.jdc.consumer.exceptions.AuthServerUnavailableException;
import com.jdc.consumer.exceptions.BusinessRuleViolationException;
import com.jdc.consumer.props.AuthenticationProps;

@Service
public class AuthConsumerProfileCheckService {
	private final RestClient authServer;
	private final AuthenticationProps properties;

	public AuthConsumerProfileCheckService(@Qualifier("introspectionHttp") RestClient authServer,
			AuthenticationProps properties) {
		this.authServer = authServer;
		this.properties = properties;
	}

	public void requirePendingConsumerProfile(String email) {
		var result = check("Consumer", email);
		if (!result.identityExists() || !result.profileExists()) {
			throw new BusinessRuleViolationException(
					"Initialize your Consumer profile on auth-server before submitting your registration.");
		}
		if (result.profileStatus() == null) {
			throw new AuthServerUnavailableException();
		}
		if (!"Pending".equals(result.profileStatus())) {
			throw new BusinessRuleViolationException(
					"Only a Pending Consumer profile can submit a self-registration.");
		}
	}

	public void requireNoConsumerProfile(String email) {
		var result = check("Consumer", email);
		if (result.profileExists()) {
			throw new BusinessRuleViolationException(
					"A Consumer profile already exists for this email address.");
		}
	}

	private EmailCheckResult check(String system, String email) {
		EmailCheckResult result;
		try {
			result = authServer.post()
					.uri(properties.getEmailCheckUri())
					.contentType(MediaType.APPLICATION_JSON)
					.body(new EmailCheckForm(system, email))
					.retrieve()
					.body(EmailCheckResult.class);
		} catch (RestClientException exception) {
			throw new AuthServerUnavailableException(exception);
		}

		if (result == null) {
			throw new AuthServerUnavailableException();
		}
		return result;
	}

	private record EmailCheckForm(String system, String email) {
	}

	private record EmailCheckResult(boolean identityExists, boolean profileExists, String profileStatus) {
	}
}
