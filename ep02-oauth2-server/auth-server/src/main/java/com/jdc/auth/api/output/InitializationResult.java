package com.jdc.auth.api.output;

import java.util.UUID;

public record InitializationResult(UUID id, String loginTicket, long expiresIn) {

}
