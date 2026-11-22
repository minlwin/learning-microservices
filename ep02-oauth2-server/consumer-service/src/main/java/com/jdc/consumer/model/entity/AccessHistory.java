package com.jdc.consumer.model.entity;

import java.time.Instant;

import com.jdc.consumer.model.entity.AccountAccess.Status;
import com.jdc.consumer.model.entity.dto.AccountAccessPk;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Data;

@Data
@Entity
public class AccessHistory {

	@EmbeddedId
	private AccountAccessPk id;
	
	@ManyToOne(optional = false)
	@JoinColumn(insertable = false, updatable = false)
	private Account account;
	
	@Column(nullable = false)
	private Status status;

	@Column(nullable = false)
	private Instant statusChangeAt;
}
