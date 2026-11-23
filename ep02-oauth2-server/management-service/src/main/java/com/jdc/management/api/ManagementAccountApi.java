package com.jdc.management.api;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jdc.management.api.input.AccountRegistrationForm;
import com.jdc.management.api.input.AccountSearch;
import com.jdc.management.api.output.AccountListItem;
import com.jdc.management.service.AccountManagementService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/accounts")
public class ManagementAccountApi {
	
	private final AccountManagementService service;

	@GetMapping
	public Page<AccountListItem> search(
			AccountSearch form, 
			@RequestParam(required = false, defaultValue = "0") int page,
			@RequestParam(required = false, defaultValue = "10") int size) {
		return service.search(form, page, size);
	}
	
	/**
	 * Creates a Pending management account after local and auth-server email checks.
	 * 
	 * <ul>
	 * <li>Check email availability through the auth-server endpoint.</li>
	 * <li>Create a local account with Pending status.</li>
	 * <li>Queue an invitation event in the Management Service outbox.</li>
	 * <li>Auth Server emails an activation link and creates an Active profile after OTP verification and password initialization.</li>
	 * <li>Consume the activation event and update local status to Activated.</li>
	 * </ul>
	 * @param form management account registration details
	 */
	@PostMapping
	public void create(@Validated @RequestBody AccountRegistrationForm form) {
		service.create(form);
	}

	@PutMapping("{id}")
	public void update(@PathVariable UUID id, @Validated @RequestBody AccountRegistrationForm form) {
		service.update(id, form);
	}
}
