package com.jdc.management.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.jdc.management.exceptions.ResourceNotFoundException;
import com.jdc.management.model.repo.AccountRepo;
import com.jdc.management.props.AuthenticationProps;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ManagementPinLockAdminService {

    private final AccountRepo accounts;
    private final AuthenticationProps properties;
    private final RestClient authServer;

    public void unlock(UUID accountId, String requestedBy) {
        var account = accounts.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("No management account was found for the provided ID."));
        var form = new PinLockUnlockForm("Management", account.getEmail(), requestedBy);
        authServer.post()
                .uri(properties.getPinLockUnlockUri())
                .body(form)
                .retrieve()
                .toBodilessEntity();
    }

    private record PinLockUnlockForm(String system, String username, String requestedBy) {
    }
}
