package com.jdc.auth.api;

import java.time.Instant;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;
import com.jdc.auth.props.AuthenticationProps;
import com.jdc.auth.service.TokenValidationService;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/internal/tokens/introspect")
public class TokenIntrospectionApi {
	private final TokenValidationService tokens;
	private final AuthenticationProps properties;

	/** Restricted to resource-server credentials; no user access token is sufficient. */
	@PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
	public ResponseEntity<Map<String, Object>> introspect(@RequestParam String token, Authentication resource) {
		Map<String, Object> result;
		try {
			var principal = tokens.validate(token);
			Collection<?> audiences = principal.getAttribute("aud");
			var allowed = properties.getResourceAudiences().getOrDefault(resource.getName(), List.of());
			if (audiences == null || audiences.stream().noneMatch(allowed::contains)) {
				throw new BadCredentialsException("Invalid audience.");
			}
				
			result = new HashMap<>(principal.getAttributes());
			result.replaceAll((_, value) -> value instanceof Instant instant ? instant.getEpochSecond() : value);
			result.put("active", true);
		} catch (BadCredentialsException exception) {
			result = Map.of("active", false);
		}
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
	}
}
