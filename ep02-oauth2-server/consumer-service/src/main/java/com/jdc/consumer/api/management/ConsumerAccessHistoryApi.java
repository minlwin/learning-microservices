package com.jdc.consumer.api.management;

import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jdc.consumer.api.management.input.AccessHistorySearch;
import com.jdc.consumer.api.management.output.AccessHistoryListItem;
import com.jdc.consumer.service.AccessHistoryService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/management/consumer/access")
public class ConsumerAccessHistoryApi {

	private final AccessHistoryService service;

	@GetMapping
	public Page<AccessHistoryListItem> search(AccessHistorySearch form,
			@RequestParam(required = false, defaultValue = "0") int page,
			@RequestParam(required = false, defaultValue = "10") int size) {
		return service.search(form, page, size);
	}
}
