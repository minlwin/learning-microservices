package com.jdc.auth.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jdc.auth.event.PinLockActivityChangedEvent;
import com.jdc.auth.exceptions.BusinessRuleViolationException;
import com.jdc.auth.exceptions.ResourceNotFoundException;
import com.jdc.auth.model.entity.PinLockHistory;
import com.jdc.auth.model.entity.PinLockSetting;
import com.jdc.auth.model.entity.PinLockState;
import com.jdc.auth.model.entity.Profile;
import com.jdc.auth.model.repo.PinLockHistoryRepo;
import com.jdc.auth.model.repo.PinLockSettingRepo;
import com.jdc.auth.model.repo.PinLockStateRepo;
import com.jdc.auth.model.repo.ProfileRepo;
import com.jdc.auth.event.service.PinLockActivityOutboxService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PinLockActivityService {

    private final ProfileRepo profiles;
    private final PinLockSettingRepo settings;
    private final PinLockStateRepo states;
    private final PinLockHistoryRepo histories;
    private final PinLockActivityOutboxService outbox;

    /** Call before PIN verification. A temporary lock is released lazily after its expiry. */
    @Transactional
    public Decision checkBeforePinAttempt(String username, String system) {
        var profile = findProfileForUpdate(username, system);
        var setting = findSetting(system);
        var state = states.findById(profile.getId()).orElse(null);
        if (state == null) {
            return new Decision(PinLockState.Status.Active, null, 0, 0);
        }

        expireTemporaryLock(state, setting, Instant.now());
        applyLoweredThresholds(state, setting, Instant.now());
        return decision(state);
    }

    /** Call only after the PIN verifier has determined that the PIN is wrong. */
    @Transactional
    public Decision recordFailedAttempt(String username, String system) {
        var profile = findProfileForUpdate(username, system);
        var setting = findSetting(system);
        var state = states.findById(profile.getId()).orElseGet(() -> newState(profile, setting));
        var now = Instant.now();

        expireTemporaryLock(state, setting, now);
        if (state.getStatus() == PinLockState.Status.PermanentlyLocked
                || state.getStatus() == PinLockState.Status.TemporarilyLocked) {
            return decision(state);
        }

        state.setFailedAttemptCount(state.getFailedAttemptCount() + 1);
        state.setFailedSinceTemporaryLock(state.getFailedSinceTemporaryLock() + 1);
        state.setLastFailedAt(now);
        state.setSettingVersion(setting.getVersion());

        if (state.getFailedAttemptCount() >= setting.getPermanentLockFailsCount()) {
            state.setStatus(PinLockState.Status.PermanentlyLocked);
            state.setLockedUntil(null);
            state.setStatusChangedAt(now);
            states.save(state);
            recordTransition(state, setting, PinLockHistory.Action.PermanentlyLocked, now, null);
            publish(state, setting, "PermanentlyLocked", now, null);
            return decision(state);
        }

        if (state.getFailedSinceTemporaryLock() >= setting.getTempLockFailsCount()) {
            var lockedUntil = now.plus(setting.getTempLockWaitMinutes(), ChronoUnit.MINUTES);
            state.setStatus(PinLockState.Status.TemporarilyLocked);
            state.setLockedUntil(lockedUntil);
            state.setFailedSinceTemporaryLock(0);
            state.setStatusChangedAt(now);
            states.save(state);
            recordTransition(state, setting, PinLockHistory.Action.TemporarilyLocked, now, null);
            publish(state, setting, "TemporarilyLocked", now, null);
            return decision(state);
        }

        states.save(state);
        return decision(state);
    }

    /** Call after a successful PIN verification; success resets the consecutive-failure counters. */
    @Transactional
    public void recordSuccessfulAttempt(String username, String system) {
        var profile = findProfileForUpdate(username, system);
        var setting = findSetting(system);
        var state = states.findById(profile.getId()).orElse(null);
        if (state == null) {
            return;
        }

        var now = Instant.now();
        expireTemporaryLock(state, setting, now);
        if (state.getStatus() != PinLockState.Status.Active) {
            throw new BusinessRuleViolationException("PIN verification is blocked by the current lock state.");
        }

        state.setFailedAttemptCount(0);
        state.setFailedSinceTemporaryLock(0);
        state.setLastFailedAt(null);
        state.setSettingVersion(setting.getVersion());
        states.save(state);
    }

    @Transactional
    public void unlockPermanently(String username, String system, String requestedBy) {
        var profile = findProfileForUpdate(username, system);
        var setting = findSetting(system);
        var state = states.findById(profile.getId())
                .orElseThrow(() -> new BusinessRuleViolationException("The profile has no PIN lock state."));
        if (state.getStatus() == PinLockState.Status.Active) {
            return;
        }
        if (state.getStatus() != PinLockState.Status.PermanentlyLocked) {
            throw new BusinessRuleViolationException("Only a permanent PIN lock can be unlocked by management.");
        }

        var now = Instant.now();
        state.setStatus(PinLockState.Status.Active);
        state.setFailedAttemptCount(0);
        state.setFailedSinceTemporaryLock(0);
        state.setLockedUntil(null);
        state.setLastFailedAt(null);
        state.setStatusChangedAt(now);
        state.setSettingVersion(setting.getVersion());
        states.save(state);

        recordTransition(state, setting, PinLockHistory.Action.PermanentUnlocked, now, requestedBy);
        publish(state, setting, "PermanentUnlocked", now, requestedBy);
    }

    private Profile findProfileForUpdate(String username, String system) {
        return profiles.findForUpdateByUsernameAndSystem(username, system)
                .orElseThrow(() -> new ResourceNotFoundException("No profile was found for the provided system and username."));
    }

    private PinLockSetting findSetting(String system) {
        return settings.findBySystemAndDeletedFalse(system)
                .orElseThrow(() -> new BusinessRuleViolationException("No active PIN lock setting is configured for this system."));
    }

    private PinLockState newState(Profile profile, PinLockSetting setting) {
        var state = new PinLockState();
        state.setProfileId(profile.getId());
        state.setProfile(profile);
        state.setStatus(PinLockState.Status.Active);
        state.setFailedAttemptCount(0);
        state.setFailedSinceTemporaryLock(0);
        state.setStatusChangedAt(Instant.now());
        state.setSettingVersion(setting.getVersion());
        return state;
    }

    private void expireTemporaryLock(PinLockState state, PinLockSetting setting, Instant now) {
        if (state.getStatus() != PinLockState.Status.TemporarilyLocked
                || state.getLockedUntil() == null || now.isBefore(state.getLockedUntil())) {
            return;
        }

        var expiredAt = state.getLockedUntil();
        state.setStatus(PinLockState.Status.Active);
        state.setLockedUntil(null);
        state.setStatusChangedAt(expiredAt);
        state.setSettingVersion(setting.getVersion());
        states.save(state);
        recordTransition(state, setting, PinLockHistory.Action.TemporaryLockExpired, expiredAt, null);
    }

    private void applyLoweredThresholds(PinLockState state, PinLockSetting setting, Instant now) {
        if (state.getStatus() != PinLockState.Status.Active) {
            return;
        }

        if (state.getFailedAttemptCount() >= setting.getPermanentLockFailsCount()) {
            state.setStatus(PinLockState.Status.PermanentlyLocked);
            state.setStatusChangedAt(now);
            state.setSettingVersion(setting.getVersion());
            states.save(state);
            recordTransition(state, setting, PinLockHistory.Action.PermanentlyLocked, now, null);
            publish(state, setting, "PermanentlyLocked", now, null);
            return;
        }

        if (state.getFailedSinceTemporaryLock() >= setting.getTempLockFailsCount()) {
            var lockedUntil = now.plus(setting.getTempLockWaitMinutes(), ChronoUnit.MINUTES);
            state.setStatus(PinLockState.Status.TemporarilyLocked);
            state.setLockedUntil(lockedUntil);
            state.setFailedSinceTemporaryLock(0);
            state.setStatusChangedAt(now);
            state.setSettingVersion(setting.getVersion());
            states.save(state);
            recordTransition(state, setting, PinLockHistory.Action.TemporarilyLocked, now, null);
            publish(state, setting, "TemporarilyLocked", now, null);
        }
    }

    private void recordTransition(PinLockState state, PinLockSetting setting,
            PinLockHistory.Action action, Instant occurredAt, String requestedBy) {
        var history = new PinLockHistory();
        history.setId(UUID.randomUUID());
        history.setProfile(state.getProfile());
        history.setAction(action);
        history.setFailedAttemptCount(state.getFailedAttemptCount());
        history.setLockedUntil(state.getLockedUntil());
        history.setSettingVersion(setting.getVersion());
        history.setOccurredAt(occurredAt);
        history.setRequestedBy(requestedBy);
        histories.save(history);
    }

    private void publish(PinLockState state, PinLockSetting setting, String action,
            Instant changedAt, String requestedBy) {
        var event = new PinLockActivityChangedEvent(
                1,
                UUID.randomUUID(),
                state.getProfileId(),
                state.getProfile().getIdentity().getUsername(),
                state.getProfile().getSystem(),
                action,
                state.getFailedAttemptCount(),
                state.getLockedUntil(),
                setting.getVersion(),
                changedAt,
                requestedBy);
        outbox.enqueue(event);
    }

    private Decision decision(PinLockState state) {
        return new Decision(state.getStatus(), state.getLockedUntil(),
                state.getFailedAttemptCount(), state.getFailedSinceTemporaryLock());
    }

    public record Decision(PinLockState.Status status, Instant lockedUntil,
            int failedAttemptCount, int failedSinceTemporaryLock) {
        public boolean isLockedAt(Instant now) {
            return status == PinLockState.Status.PermanentlyLocked
                    || (status == PinLockState.Status.TemporarilyLocked
                            && lockedUntil != null && now.isBefore(lockedUntil));
        }
    }
}
