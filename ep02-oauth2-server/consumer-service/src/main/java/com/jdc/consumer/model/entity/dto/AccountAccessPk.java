package com.jdc.consumer.model.entity.dto;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record AccountAccessPk(
		@Column(name = "account_id")
		UUID accountId,
		int revision) {

}
