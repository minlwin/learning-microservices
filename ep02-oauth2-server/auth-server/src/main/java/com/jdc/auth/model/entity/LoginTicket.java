package com.jdc.auth.model.entity;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
public class LoginTicket {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;
	@Column(nullable = false, unique = true, length = 64)
	private String tokenHash;
	@ManyToOne(optional = false)
	private Profile profile;
	@Column(nullable = false)
	private String clientId;
	@Column(nullable = false)
	private Instant expiresAt;
	private Instant consumedAt;
	@Column(nullable = false)
	private long tokenVersion;
	@Column(nullable = false)
	private boolean onboarding;


}
