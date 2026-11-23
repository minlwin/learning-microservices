package com.jdc.management.event.listener;

import java.time.Instant;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.jdc.management.event.ManagementAccountActivated;
import com.jdc.management.exceptions.BusinessRuleViolationException;
import com.jdc.management.exceptions.ResourceNotFoundException;
import com.jdc.management.model.entity.Account;
import com.jdc.management.model.repo.AccountRepo;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class ManagementAccountActivatedEventListener {

    private final ObjectMapper objectMapper;
    private final AccountRepo accounts;

    @KafkaListener(topics = "#{@kafkaProps.managementAccount.activatedTopic}",
            groupId = "#{@kafkaProps.managementAccount.activatedConsumerGroupId}")
    @Transactional
    public void onManagementAccountActivated(String payload) {
        var event = deserialize(payload);
        validate(event);
        var account = accounts.findForUpdate(event.accountId()).orElseThrow(
                () -> new ResourceNotFoundException("Management account for activation event was not found."));
        if (!account.getEmail().equals(event.email())) {
            throw new BusinessRuleViolationException("The activation event email does not match the management account.");
        }
        if (account.getStatus() == Account.Status.Activated) {
            return;
        }
        if (account.getStatus() != Account.Status.Pending) {
            throw new BusinessRuleViolationException("Only a Pending management account can be activated.");
        }
        account.setStatus(Account.Status.Activated);
        account.setActivatedAt(event.activatedAt() == null ? Instant.now() : event.activatedAt());
    }

    private void validate(ManagementAccountActivated event) {
        if (event == null || event.schemaVersion() != 1 || event.eventId() == null || event.accountId() == null
                || event.email() == null || event.email().isBlank()) {
            throw new IllegalArgumentException("Management account activation event is invalid.");
        }
    }

    private ManagementAccountActivated deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, ManagementAccountActivated.class);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Could not deserialize the management account activation event.", exception);
        }
    }
}
