package com.jdc.auth.api;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jdc.auth.api.input.EmailCheckForm;
import com.jdc.auth.api.output.EmailCheckResult;
import com.jdc.auth.service.EmailCheckService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/internal/profiles/email/check")
public class EmailCheckApi {
	
	private final EmailCheckService service;

	/** Advisory check; final profile creation must still enforce uniqueness. */
	@PostMapping
	public ResponseEntity<EmailCheckResult> check(@Validated @RequestBody EmailCheckForm form, Authentication resource) {
		var result = service.check(resource.getName(), form);
		var response = ResponseEntity.ok();
		response.cacheControl(CacheControl.noStore());
		response.header("Pragma", "no-cache");
		return response.body(result);
	}
}
