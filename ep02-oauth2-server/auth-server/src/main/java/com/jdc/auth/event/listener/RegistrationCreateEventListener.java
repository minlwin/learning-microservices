package com.jdc.auth.event.listener;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jdc.auth.event.ConsumerAccountApproved;
import com.jdc.auth.exceptions.BusinessRuleViolationException;
import com.jdc.auth.model.entity.Profile;
import com.jdc.auth.model.repo.ProfileRepo;
import com.jdc.auth.service.RegistrationService;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class RegistrationCreateEventListener {
	private final ObjectMapper objectMapper;
	private final ProfileRepo profiles;
	private final RegistrationService registrations;

	@KafkaListener(topics = "#{@kafkaProps.consumerAccount.approvedTopic}",
			groupId = "#{@kafkaProps.consumerAccount.approvedGroupId}")
	@Transactional
	public void onConsumerAccountApproved(String payload) {
		var event = deserialize(payload);
		validate(event);
		if (!"Consumer".equals(event.system())) {
			throw new BusinessRuleViolationException("Unsupported account system in approval event.");
		}

		switch (event.registrationType()) {
		case "Self" -> activatePendingProfile(event.email());
		case "Management" -> registrations.createManagementInvitation(event.eventId(), event.email());
		default -> throw new BusinessRuleViolationException("Unsupported Consumer registration type.");
		}
	}

	private void activatePendingProfile(String email) {
		var profile = profiles.findForUpdateByUsernameAndSystem(email, "Consumer")
				.orElseThrow(() -> new BusinessRuleViolationException(
						"The Pending Consumer profile was not found for the approved account."));
		if (profile.getStatus() == Profile.Status.Active) {
			return;
		}
		if (profile.getStatus() != Profile.Status.Pending) {
			throw new BusinessRuleViolationException("Only a Pending Consumer profile can be activated.");
		}
		profile.setStatus(Profile.Status.Active);
	}

	private ConsumerAccountApproved deserialize(String payload) {
		try {
			return objectMapper.readValue(payload, ConsumerAccountApproved.class);
		} catch (JacksonException exception) {
			throw new IllegalArgumentException("Could not deserialize Consumer account approval event.", exception);
		}
	}

	private void validate(ConsumerAccountApproved event) {
		if (event == null || event.eventId() == null || event.consumerAccountId() == null
				|| event.email() == null || event.email().isBlank() || event.system() == null
				|| event.registrationType() == null) {
			throw new IllegalArgumentException("Consumer account approval event is invalid.");
		}
	}

}
