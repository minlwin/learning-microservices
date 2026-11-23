package com.jdc.management.api;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jdc.management.service.ManagementPinLockAdminService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/accounts")
public class PinLockManagementApi {

    private final ManagementPinLockAdminService service;

    @PutMapping("{id}/pin-lock/unlock")
    public void unlock(@PathVariable UUID id, @AuthenticationPrincipal OAuth2AuthenticatedPrincipal principal) {
        service.unlock(id, principal.getName());
    }
}
