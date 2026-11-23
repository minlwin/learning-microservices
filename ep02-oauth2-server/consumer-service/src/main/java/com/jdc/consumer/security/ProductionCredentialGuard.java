package com.jdc.consumer.security;

import java.net.URI;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.jdc.consumer.props.AuthenticationProps;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ProductionCredentialGuard implements ApplicationRunner {

    private final Environment environment;
    private final AuthenticationProps authenticationProps;

    @Override
    public void run(ApplicationArguments arguments) {
        if (!environment.matchesProfiles("prod", "production")) {
            return;
        }
        requireProductionSecret("consumer resource", authenticationProps.getResourceSecret(),
                "consumer-resource-secret");
        requireProductionSecret("consumer database", environment.getProperty("spring.datasource.password"),
                "csmpass");
        requireProductionEndpoint("Auth Server issuer", authenticationProps.getIssuer(), true);
        requireProductionEndpoint("Auth Server introspection endpoint", authenticationProps.getIntrospectionUri(), false);
        requireProductionEndpoint("Auth Server PIN unlock endpoint", authenticationProps.getPinLockUnlockUri(), false);
        requireProductionEndpoint("Auth Server email check endpoint", authenticationProps.getEmailCheckUri(), false);
    }

    private void requireProductionSecret(String name, String value, String developmentDefault) {
        if (value == null || value.isBlank() || developmentDefault.equals(value)) {
            throw new IllegalStateException("A non-default secret must be configured for " + name + " in production.");
        }
    }

    private void requireProductionEndpoint(String name, String value, boolean requireHttps) {
        var uri = URI.create(value);
        var validScheme = "https".equalsIgnoreCase(uri.getScheme())
                || (!requireHttps && "http".equalsIgnoreCase(uri.getScheme()));
        if (!validScheme || uri.getHost() == null || isLoopback(uri.getHost())) {
            throw new IllegalStateException(name + " must use a configured non-localhost URL in production.");
        }
    }

    private boolean isLoopback(String host) {
        var normalized = host.toLowerCase(java.util.Locale.ROOT).replace("[", "").replace("]", "");
        return "localhost".equals(normalized) || normalized.endsWith(".localhost")
                || normalized.startsWith("127.") || "::1".equals(normalized);
    }
}
