package com.jdc.auth.event.listener;

import java.time.Instant;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.jdc.auth.event.ManagementAccountInvitationRequested;
import com.jdc.auth.exceptions.BusinessRuleViolationException;
import com.jdc.auth.model.entity.Activation;
import com.jdc.auth.model.entity.EmailOutboxEvent;
import com.jdc.auth.model.repo.ActivationRepo;
import com.jdc.auth.model.repo.EmailOutboxEventRepo;
import com.jdc.auth.props.AuthenticationProps;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class ManagementAccountInvitationEventListener {

    private final ObjectMapper objectMapper;
    private final ActivationRepo activations;
    private final EmailOutboxEventRepo emailEvents;
    private final AuthenticationProps authenticationProps;

    @KafkaListener(topics = "#{@kafkaProps.managementAccount.invitationTopic}",
            groupId = "#{@kafkaProps.managementAccount.invitationGroupId}")
    @Transactional
    public void onManagementAccountInvitationRequested(String payload) {
        var event = deserialize(payload);
        validate(event);
        var existing = activations.findById(event.accountId());
        if (existing.isPresent()) {
            if (!existing.get().getEmail().equals(event.email())) {
                throw new BusinessRuleViolationException("The invitation email does not match its existing activation.");
            }
            return;
        }

        var now = Instant.now();
        var activation = new Activation();
        activation.setId(event.accountId());
        activation.setEmail(event.email());
        activation.setInvitationExpiresAt(now.plus(authenticationProps.getManagementActivationLifetime()));
        activations.save(activation);

        var emailEvent = new EmailOutboxEvent();
        emailEvent.setId(event.eventId());
        emailEvent.setRecipient(event.email());
        emailEvent.setSystem("Management");
        emailEvent.setInvitationUrl(authenticationProps.getManagementActivationUrl()
                .replace("{id}", event.accountId().toString()));
        emailEvent.setCreatedAt(now);
        emailEvent.setNextAttemptAt(now);
        emailEvent.setAttemptCount(0);
        emailEvents.save(emailEvent);
    }

    private void validate(ManagementAccountInvitationRequested event) {
        if (event == null || event.schemaVersion() != 1 || event.eventId() == null || event.accountId() == null
                || event.email() == null || event.email().isBlank()) {
            throw new IllegalArgumentException("Management account invitation event is invalid.");
        }
    }

    private ManagementAccountInvitationRequested deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, ManagementAccountInvitationRequested.class);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Could not deserialize the management account invitation event.", exception);
        }
    }
}
