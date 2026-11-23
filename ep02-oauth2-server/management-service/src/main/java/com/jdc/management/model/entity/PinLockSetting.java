package com.jdc.management.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import lombok.Data;

@Data
@Entity
public class PinLockSetting {

	@Id
	private String system;
	
	@Column(nullable = false)
	private int tempLockFailsCount;
	
	@Column(nullable = false)
	private int tempLockWailtMinutes;
	
	@Column(nullable = false)
	private int permenantLockFailsCount;

	@Column(nullable = false)
	private boolean deleted;

	@Version
	@Column(nullable = false)
	private long version;
	
}
