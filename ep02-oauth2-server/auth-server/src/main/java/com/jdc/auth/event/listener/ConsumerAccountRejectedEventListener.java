package com.jdc.auth.event.listener;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.jdc.auth.event.ConsumerAccountRejected;
import com.jdc.auth.exceptions.BusinessRuleViolationException;
import com.jdc.auth.model.entity.Profile;
import com.jdc.auth.model.repo.ProfileRepo;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class ConsumerAccountRejectedEventListener {
	private final ObjectMapper objectMapper;
	private final ProfileRepo profiles;

	@KafkaListener(topics = "#{@kafkaProps.consumerAccount.rejectedTopic}",
			groupId = "#{@kafkaProps.consumerAccount.rejectedGroupId}")
	@Transactional
	public void onConsumerAccountRejected(String payload) {
		var event = deserialize(payload);
		if (event.eventId() == null || event.consumerAccountId() == null || event.email() == null
				|| !"Consumer".equals(event.system())) {
			throw new IllegalArgumentException("Consumer account rejection event is invalid.");
		}
		if (!"Self".equals(event.registrationType())) {
			return;
		}
		var profile = profiles.findForUpdateByUsernameAndSystem(event.email(), "Consumer")
				.orElseThrow(() -> new BusinessRuleViolationException("Pending Consumer profile was not found."));
		if (profile.getStatus() == Profile.Status.Closed) {
			return;
		}
		if (profile.getStatus() != Profile.Status.Pending) {
			throw new BusinessRuleViolationException("Only a Pending Consumer profile can be closed after rejection.");
		}
		profile.setStatus(Profile.Status.Closed);
	}

	private ConsumerAccountRejected deserialize(String payload) {
		try {
			return objectMapper.readValue(payload, ConsumerAccountRejected.class);
		} catch (JacksonException exception) {
			throw new IllegalArgumentException("Could not deserialize Consumer account rejection event.", exception);
		}
	}
}
