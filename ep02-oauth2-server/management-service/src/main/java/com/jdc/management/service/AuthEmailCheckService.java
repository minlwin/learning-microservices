package com.jdc.management.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import com.jdc.management.exceptions.BusinessRuleViolationException;
import com.jdc.management.exceptions.AuthServerUnavailableException;
import com.jdc.management.props.AuthenticationProps;

@Service
public class AuthEmailCheckService {
    private final RestClient http;
    private final AuthenticationProps properties;

    public AuthEmailCheckService(@Qualifier("introspectionHttp") RestClient http, AuthenticationProps properties) {
        this.http = http;
        this.properties = properties;
    }

    public void checkAvailable(String email) {
        EmailCheckResult result;
        try {
            var request = http.post();
            request.uri(properties.getEmailCheckUri());
            request.contentType(MediaType.APPLICATION_JSON);
            request.body(new EmailCheckForm("Management", email));
            var response = request.retrieve();
            result = response.body(EmailCheckResult.class);
        } catch (RestClientException exception) {
            throw new AuthServerUnavailableException();
        }
        if (result == null || result.profileExists() == null) {
            throw new AuthServerUnavailableException();
        }
        if (result.profileExists()) {
            throw new BusinessRuleViolationException("A Management profile already exists for this email address.");
        }
    }

    private record EmailCheckForm(String system, String email) {
    }

    private record EmailCheckResult(Boolean identityExists, Boolean profileExists, String profileStatus) {
    }
}
