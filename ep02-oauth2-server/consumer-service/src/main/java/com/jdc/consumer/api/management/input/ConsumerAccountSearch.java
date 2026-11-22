package com.jdc.consumer.api.management.input;

import java.time.LocalDate;

import com.jdc.consumer.model.entity.Account.RegistrationType;
import com.jdc.consumer.model.entity.Account.Status;

public record ConsumerAccountSearch(
		Status status, 
		RegistrationType registrationType,
		LocalDate createdFrom,
		LocalDate createdTo,
		String keyword // Name, Phone or email with ignore case start search 
) {

}
