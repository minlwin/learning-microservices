package com.jdc.consumer.api.management;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;

import com.jdc.consumer.api.management.input.LockHistorySearch;
import com.jdc.consumer.api.management.output.LockHistoryListItem;
import com.jdc.consumer.service.LockStatusService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/management/consumer/lock")
public class ConsumerLockStatusManagementApi {

	private final LockStatusService service;

	@GetMapping
	public Page<LockHistoryListItem> search(LockHistorySearch form,
			@RequestParam(required = false, defaultValue = "0") int page,
			@RequestParam(required = false, defaultValue = "10") int size) {
		return service.search(form, page, size);
	}

	@PutMapping("{id}/unlock")
	public void unlockPermanentPin(@PathVariable UUID id,
			@AuthenticationPrincipal OAuth2AuthenticatedPrincipal principal) {
		service.unlockPermanentPin(id, principal.getName());
	}
}
