package com.jdc.management.api;

import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jdc.management.api.input.PinLockSettingForm;
import com.jdc.management.api.output.PinLockSettingDto;
import com.jdc.management.service.PinLockSettingService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/setting/pin-locks")
public class PinLockSettingApi {
	
	private final PinLockSettingService service;
	
	@GetMapping
	public List<PinLockSettingDto> getAllSettings() {
		return service.getAllSettings();
	}
	
	@PostMapping("{system}")
	public void create(
			@PathVariable String system, 
			@Validated @RequestBody PinLockSettingForm form) {
		service.create(system, form);
	}
	
	@PutMapping("{system}")
	public void update(
			@PathVariable String system, 
			@Validated @RequestBody PinLockSettingForm form) {
		service.update(system, form);
	}

	@GetMapping("systems")
	public List<String> getAvailableSystem() {
		return service.getAvailableSystem();
	}
}
