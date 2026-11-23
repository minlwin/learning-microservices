package com.jdc.auth.api.secured;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jdc.auth.api.input.PinLockUnlockForm;
import com.jdc.auth.props.AuthenticationProps;
import com.jdc.auth.service.PinLockActivityService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/internal/pin-locks")
public class PinLockAdminApi {

    private final PinLockActivityService service;
    private final AuthenticationProps properties;

    @PostMapping("/unlock")
    public void unlock(@Validated @RequestBody PinLockUnlockForm form, Authentication resource) {
        var client = properties.getResources().get(resource.getName());
        if (client == null || !client.getPinLockSystems().contains(form.system())) {
            throw new AccessDeniedException("This resource cannot unlock PIN locks for the requested system.");
        }
        service.unlockPermanently(form.username(), form.system(), form.requestedBy());
    }
}
