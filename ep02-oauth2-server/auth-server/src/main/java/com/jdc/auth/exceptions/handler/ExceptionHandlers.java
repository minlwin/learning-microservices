package com.jdc.auth.exceptions.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.http.converter.OAuth2ErrorHttpMessageConverter;
import org.springframework.web.context.request.ServletWebRequest;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.mail.MailException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import com.jdc.auth.exceptions.BusinessRuleViolationException;
import com.jdc.auth.exceptions.ResourceNotFoundException;

/** Shared ProblemDetail responses for controllers and delegated security errors. */
@RestControllerAdvice
public class ExceptionHandlers extends ResponseEntityExceptionHandler {
	private static final Logger log = LoggerFactory.getLogger(ExceptionHandlers.class);
	public record ValidationError(String field, String message) { }

	/** OAuth protocol errors keep their standard error format, with policy centralized here. */
	public void oauth2Failure(HttpServletRequest request,
			HttpServletResponse response, AuthenticationException exception)
			throws IOException {
		String code = exception instanceof OAuth2AuthenticationException oauth
				? oauth.getError().getErrorCode() : "server_error";
		response.setStatus("invalid_client".equals(code) ? 401 : "server_error".equals(code) ? 500 : 400);
		response.setHeader("Cache-Control", "no-store");
		response.setHeader("Pragma", "no-cache");
		var converter = new OAuth2ErrorHttpMessageConverter();
		converter.write(new OAuth2Error(code), MediaType.APPLICATION_JSON,
				new ServletServerHttpResponse(response));
	}

	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<Object> authentication(AuthenticationException exception, WebRequest request) {
		return problem(HttpStatus.UNAUTHORIZED, "Authentication failed. Please check your credentials or sign in again.", request);
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<Object> accessDenied(AccessDeniedException exception, WebRequest request) {
		return problem(HttpStatus.FORBIDDEN, "You do not have permission to access this resource.", request);
	}

	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<Object> notFound(ResourceNotFoundException exception, WebRequest request) {
		return problem(HttpStatus.NOT_FOUND, exception.getMessage(), request);
	}

	@ExceptionHandler(BusinessRuleViolationException.class)
	public ResponseEntity<Object> businessRule(BusinessRuleViolationException exception, WebRequest request) {
		return problem(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<Object> invalidArgument(IllegalArgumentException exception, WebRequest request) {
		return problem(HttpStatus.BAD_REQUEST, "The request contains an invalid value.", request);
	}

	@ExceptionHandler({DataIntegrityViolationException.class, ConcurrencyFailureException.class})
	public ResponseEntity<Object> conflict(RuntimeException exception, WebRequest request) {
		log.warn("Database conflict while processing a request", exception);
		return problem(HttpStatus.CONFLICT, "The request conflicts with existing data or a concurrent update.", request);
	}

	@ExceptionHandler(MailException.class)
	public ResponseEntity<Object> mail(MailException exception, WebRequest request) {
		log.error("Email delivery failed", exception);
		return problem(HttpStatus.SERVICE_UNAVAILABLE, "We could not send the email. Please try again later.", request);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<Object> unexpected(Exception exception, WebRequest request) {
		log.error("Unexpected request failure", exception);
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred. Please try again later.", request);
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		var detail = ProblemDetail.forStatusAndDetail(status, "Please correct the invalid request fields.");
		List<ValidationError> errors = exception.getBindingResult().getFieldErrors().stream()
				.map(error -> new ValidationError(error.getField(), error.getDefaultMessage())).toList();
		detail.setProperty("errors", errors);
		return handleExceptionInternal(exception, detail, headers, status, request);
	}

	private ResponseEntity<Object> problem(HttpStatus status, String message, WebRequest request) {
		var detail = ProblemDetail.forStatusAndDetail(status, message);
		return createResponseEntity(detail, new HttpHeaders(), status, request);
	}

	@Override
	protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		var resultHeaders = new HttpHeaders();
		resultHeaders.putAll(headers);
		resultHeaders.setCacheControl(CacheControl.noStore());
		resultHeaders.setPragma("no-cache");
		if(body instanceof ProblemDetail detail) {
			if(detail.getInstance() == null && request instanceof ServletWebRequest servlet) {
				detail.setInstance(URI.create(servlet.getRequest().getRequestURI()));
			}
		}
		return new ResponseEntity<>(body, resultHeaders, status);
	}
}
