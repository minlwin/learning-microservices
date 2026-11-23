package com.jdc.management.api;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jdc.management.api.input.PinLockHistorySearch;
import com.jdc.management.api.output.PinLockHistoryListItem;
import com.jdc.management.service.ManagementPinLockAdminService;
import com.jdc.management.service.PinLockHistoryService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/accounts/pin-lock")
public class PinLockManagementApi {

    private final ManagementPinLockAdminService service;
    private final PinLockHistoryService historyService;

    @GetMapping
    public Page<PinLockHistoryListItem> search(
            PinLockHistorySearch form,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "10") int size) {
        return historyService.search(form, page, size);
    }

    @PutMapping("{id}/unlock")
    public void unlock(@PathVariable UUID id, @AuthenticationPrincipal OAuth2AuthenticatedPrincipal principal) {
        service.unlock(id, principal.getName());
    }
}
