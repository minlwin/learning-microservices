package com.jdc.management.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jdc.management.api.input.PinLockSettingForm;
import com.jdc.management.api.output.PinLockSettingDto;
import com.jdc.management.exceptions.BusinessRuleViolationException;
import com.jdc.management.exceptions.ResourceNotFoundException;
import com.jdc.management.model.entity.PinLockSetting;
import com.jdc.management.model.repo.PinSettingRepo;
import com.jdc.management.event.PinLockSettingChangedEvent;
import com.jdc.management.event.service.PinLockSettingOutboxService;
import com.jdc.management.props.ClientSystemProps;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PinLockSettingService {
    private final PinSettingRepo repository;
    private final ClientSystemProps clientSystems;
    private final PinLockSettingOutboxService outboxService;

    public List<PinLockSettingDto> getAllSettings() {
        var settings = repository.findByDeletedFalseOrderBySystemAsc();
        return settings.stream().map(PinLockSettingDto::from).toList();
    }

    public List<String> getAvailableSystem() {
        var settings = repository.findByDeletedFalseOrderBySystemAsc();
        var configuredSystems = settings.stream().map(PinLockSetting::getSystem).toList();
        return clientSystems.getSystems().stream()
                .filter(system -> !configuredSystems.contains(system)).distinct().sorted().toList();
    }

    @Transactional
    public void create(String system, PinLockSettingForm form) {
        validate(system, form);
        var existing = repository.findById(system);
        if (existing.isPresent() && !existing.get().isDeleted()) {
            throw new BusinessRuleViolationException("PIN lock settings already exist for this system.");
        }
        var entity = existing.orElseGet(PinLockSetting::new);
        entity.setSystem(system);
        entity.setDeleted(false);
        apply(entity, form);
        repository.saveAndFlush(entity);
        outboxService.enqueue(PinLockSettingChangedEvent.from(entity));
    }

    @Transactional
    public void update(String system, PinLockSettingForm form) {
        validate(system, form);
        var entity = repository.findById(system)
                .filter(setting -> !setting.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("PIN lock settings were not found for this system."));
        apply(entity, form);
        repository.saveAndFlush(entity);
        outboxService.enqueue(PinLockSettingChangedEvent.from(entity));
    }

    private void validate(String system, PinLockSettingForm form) {
        if (!clientSystems.getSystems().contains(system)) {
            throw new BusinessRuleViolationException("The client system is not configured.");
        }
        if (form.tempLockFailsCount() < 1 || form.tempLockWaitMinutes() < 1
                || form.permanentLockFailsCount() <= form.tempLockFailsCount()) {
            throw new BusinessRuleViolationException(
                    "Lock limits must be positive and the permanent-lock threshold must exceed the temporary-lock threshold.");
        }
    }

    private void apply(PinLockSetting entity, PinLockSettingForm form) {
        entity.setTempLockFailsCount(form.tempLockFailsCount());
        entity.setTempLockWailtMinutes(form.tempLockWaitMinutes());
        entity.setPermenantLockFailsCount(form.permanentLockFailsCount());
    }
}
