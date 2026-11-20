package com.jdc.auth.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jdc.auth.exceptions.BusinessRuleViolationException;
import com.jdc.auth.model.repo.RegistrationRepo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmailValidationService {
	
	private final RegistrationRepo registrationRepo;
	
	@Transactional(readOnly = true)
	public void validateForRegistration(String email) {
		// Check already exists
		if(registrationRepo.countByEmail(email) > 0) {
			throw new BusinessRuleViolationException("Your email is already registered as a consumer.");
		}
	}

}
