package com.jdc.auth.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jdc.auth.api.input.EmailCheckForm;
import com.jdc.auth.api.output.EmailCheckResult;
import com.jdc.auth.exceptions.BusinessRuleViolationException;
import com.jdc.auth.model.repo.ProfileRepo;
import com.jdc.auth.props.AuthenticationProps;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmailCheckService {
	private final ProfileRepo profiles;
	private final AuthenticationProps properties;

	public EmailCheckResult check(String resourceId, EmailCheckForm form) {
		var resource = properties.getResources().get(resourceId);
		if (resource == null || !resource.getEmailCheckSystems().contains(form.system())) {
			throw new AccessDeniedException("This service cannot check emails for the requested system.");
		}
		if (!properties.getSystems().containsKey(form.system())) {
			throw new BusinessRuleViolationException("The requested system is not configured.");
		}
		return new EmailCheckResult(profiles.existsByIdentityUsernameAndSystem(form.email(), form.system()));
	}
}
