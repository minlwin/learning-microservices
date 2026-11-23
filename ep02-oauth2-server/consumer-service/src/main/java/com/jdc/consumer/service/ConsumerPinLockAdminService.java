package com.jdc.consumer.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.jdc.consumer.exceptions.ResourceNotFoundException;
import com.jdc.consumer.model.repo.AccountRepo;
import com.jdc.consumer.props.AuthenticationProps;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ConsumerPinLockAdminService {

    private final AccountRepo accounts;
    private final AuthenticationProps properties;
    private final RestClient authServer;

    public void unlock(UUID accountId, String requestedBy) {
        var account = accounts.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("No consumer account was found for the provided ID."));
        var form = new PinLockUnlockForm("Consumer", account.getEmail(), requestedBy);
        authServer.post()
                .uri(properties.getPinLockUnlockUri())
                .body(form)
                .retrieve()
                .toBodilessEntity();
    }

    private record PinLockUnlockForm(String system, String username, String requestedBy) {
    }
}
