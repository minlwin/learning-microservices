package com.jdc.auth.model.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

@Data
@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_profile_identity_system", columnNames = {"identity_id", "system"}))
public class Profile {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;
		
	@ManyToOne(optional = false)
	private Identity identity;
	
	@Column(nullable = false)
	private System system;
	
	@Column(nullable = false)
	private Role role;
	
	@Column(nullable = false)
	private String password;
	
	@Column(nullable = false)
	private Status status;
	
	@Column(nullable = false)
	private long tokenVersion;

	public enum System {
		Consumer, Management
	}
	
	public enum Role {
		Consumer, Management, Administrator
	}
	
	public enum Status {
		Pending, Active, Locked, Closed
	}
}
