package com.jdc.auth.event.listener;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.jdc.auth.event.PinLockSettingChangedEvent;
import com.jdc.auth.service.PinLockSettingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class PinLockSettingEventListener {

    private final ObjectMapper objectMapper;
    private final PinLockSettingService service;

    @KafkaListener(
            topics = "#{@kafkaProps.pinLock.topic}",
            groupId = "#{@kafkaProps.pinLock.consumerGroupId}")
    public void onPinLockSettingChanged(String payload) {
        var event = deserialize(payload);
        service.apply(event);
        log.info("Applied PIN lock setting event {} for system {} at version {}.",
                event.eventId(), event.system(), event.version());
    }

    private PinLockSettingChangedEvent deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, PinLockSettingChangedEvent.class);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Could not deserialize the PIN lock setting event.", exception);
        }
    }
}
