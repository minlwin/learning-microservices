package com.jdc.consumer.api.management;

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

import com.jdc.consumer.api.management.input.ConsumerAccountSearch;
import com.jdc.consumer.api.management.input.ConsumerRegistrationForm;
import com.jdc.consumer.api.management.output.ConsumerAccountListItem;
import com.jdc.consumer.service.ConsumerAccountService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/management/consumer")
public class ConsumerManagementApi {
	
	private final ConsumerAccountService service;

	@GetMapping
	public Page<ConsumerAccountListItem> search(ConsumerAccountSearch form, 
			@RequestParam(required = false, defaultValue = "0") int page,
			@RequestParam(required = false, defaultValue = "10") int size) {
		return service.search(form, page, size);
	}
	
	@PostMapping
	public void create(@RequestBody @Validated ConsumerRegistrationForm form) {
		service.register(form);
	}
	
	@PutMapping("{id}/approve")
	public void approve(
			@PathVariable UUID id) {
		service.approve(id);
	}

	@PutMapping("{id}/reject")
	public void reject(@PathVariable UUID id) {
		service.reject(id);
	}
	
}
