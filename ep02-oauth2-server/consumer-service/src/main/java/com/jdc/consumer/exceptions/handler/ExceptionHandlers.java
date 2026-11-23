package com.jdc.consumer.exceptions.handler;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.jdc.consumer.exceptions.BusinessRuleViolationException;
import com.jdc.consumer.exceptions.AuthServerUnavailableException;
import com.jdc.consumer.exceptions.ResourceNotFoundException;
import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class ExceptionHandlers extends ResponseEntityExceptionHandler {
	@ExceptionHandler(ResourceNotFoundException.class)
	public ProblemDetail notFound(ResourceNotFoundException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
	}

	@ExceptionHandler(BusinessRuleViolationException.class)
	public ProblemDetail businessRule(BusinessRuleViolationException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
	}

	@ExceptionHandler(AuthServerUnavailableException.class)
	public ProblemDetail authServerUnavailable(AuthServerUnavailableException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
	}

	@ExceptionHandler({IllegalArgumentException.class, ConstraintViolationException.class})
	public ProblemDetail invalidRequest(RuntimeException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "The request contains an invalid value.");
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ProblemDetail conflict(DataIntegrityViolationException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "The request conflicts with existing account data.");
	}

	@ExceptionHandler(AuthenticationException.class)
	public ProblemDetail authentication(AuthenticationException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Sign in with a valid consumer account.");
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ProblemDetail accessDenied(AccessDeniedException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "You do not have permission to perform this action.");
	}
}
