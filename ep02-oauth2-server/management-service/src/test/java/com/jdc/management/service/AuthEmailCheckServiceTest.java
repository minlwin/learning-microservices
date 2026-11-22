package com.jdc.management.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import com.jdc.management.exceptions.AuthServerUnavailableException;
import com.jdc.management.exceptions.BusinessRuleViolationException;
import com.jdc.management.props.AuthenticationProps;

class AuthEmailCheckServiceTest {
    @Test
    void checksManagementSystemAndRejectsUnavailableEmailOrServer() {
        var props = new AuthenticationProps();
        var builder = RestClient.builder();
        builder.defaultHeaders(headers -> headers.setBasicAuth("management-service", "secret"));
        var server = MockRestServiceServer.bindTo(builder).build();
        var service = new AuthEmailCheckService(builder.build(), props);
        for (var body : new String[] {"{\"exists\":false}", "{\"exists\":true}", "{}"}) {
            server.expect(requestTo(props.getEmailCheckUri())).andExpect(method(HttpMethod.POST))
                    .andExpect(header("Authorization", "Basic bWFuYWdlbWVudC1zZXJ2aWNlOnNlY3JldA=="))
                    .andExpect(content().json("{\"system\":\"Management\",\"email\":\"alice@example.com\"}"))
                    .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        }
        server.expect(requestTo(props.getEmailCheckUri())).andRespond(withServerError());
        assertDoesNotThrow(() -> service.checkAvailable("alice@example.com"));
        assertThrows(BusinessRuleViolationException.class, () -> service.checkAvailable("alice@example.com"));
        assertThrows(AuthServerUnavailableException.class, () -> service.checkAvailable("alice@example.com"));
        assertThrows(AuthServerUnavailableException.class, () -> service.checkAvailable("alice@example.com"));
        server.verify();
    }
}
