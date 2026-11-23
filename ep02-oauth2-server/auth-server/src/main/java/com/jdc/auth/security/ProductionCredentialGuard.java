package com.jdc.auth.security;

import java.util.Set;
import java.net.URI;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.jdc.auth.props.AdministratorProps;
import com.jdc.auth.props.AuthenticationProps;

import lombok.RequiredArgsConstructor;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class ProductionCredentialGuard implements ApplicationRunner {

    private static final Set<String> DEVELOPMENT_SECRETS = Set.of(
            "consumer-resource-secret", "management-resource-secret", "consumer-demo-secret",
            "management-demo-secret", "password", "ChangeThisLocalAdminPassword1");

    private final Environment environment;
    private final AuthenticationProps authenticationProps;
    private final AdministratorProps administratorProps;

    @Override
    public void run(ApplicationArguments arguments) {
        if (!environment.matchesProfiles("prod", "production")) {
            return;
        }
        authenticationProps.getResources().forEach((id, resource) -> requireProductionSecret(
                "resource " + id, resource.getSecret()));
        authenticationProps.getClients().forEach((id, client) -> requireProductionSecret(
                "client " + id, client.getSecret()));
        requireProductionSecret("administrator", administratorProps.getPassword());
        requireProductionSecret("auth database", environment.getProperty("spring.datasource.password"));
        if ("admin@example.com".equals(administratorProps.getUsername())) {
            throw new IllegalStateException("The default administrator username must be changed for production.");
        }
        var defaultSigningDirectory = java.nio.file.Path.of(System.getProperty("user.home"), ".wallet-demo");
        if (authenticationProps.getSigningKeys().getPrivateKeyPath().toAbsolutePath().normalize()
                        .startsWith(defaultSigningDirectory.toAbsolutePath().normalize())
                || authenticationProps.getSigningKeys().getPublicKeyPath().toAbsolutePath().normalize()
                        .startsWith(defaultSigningDirectory.toAbsolutePath().normalize())) {
            throw new IllegalStateException("Production Auth Server signing keys must use provisioned key files.");
        }
        requireProductionHttps("Auth Server issuer", authenticationProps.getIssuer());
        requireProductionHttps("Consumer invitation URL", authenticationProps.getConsumerInvitationUrl());
        requireProductionHttps("Management activation URL", authenticationProps.getManagementActivationUrl());
    }

    private void requireProductionSecret(String name, String secret) {
        if (secret == null || secret.isBlank() || DEVELOPMENT_SECRETS.contains(secret)) {
            throw new IllegalStateException("A non-default secret must be configured for " + name + " in production.");
        }
    }

    private void requireProductionHttps(String name, String value) {
        var uri = URI.create(value.replace("{id}", "activation-id"));
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || isLoopback(uri.getHost())) {
            throw new IllegalStateException(name + " must be an externally reachable HTTPS URL in production.");
        }
    }

    private boolean isLoopback(String host) {
        var normalized = host.toLowerCase(java.util.Locale.ROOT).replace("[", "").replace("]", "");
        return "localhost".equals(normalized) || normalized.endsWith(".localhost")
                || normalized.startsWith("127.") || "::1".equals(normalized);
    }
}
