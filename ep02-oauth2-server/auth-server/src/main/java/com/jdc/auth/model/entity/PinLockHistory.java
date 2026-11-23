package com.jdc.auth.model.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "pin_lock_history")
public class PinLockHistory {

    @Id
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    private Profile profile;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Action action;

    @Column(name = "failed_attempt_count", nullable = false)
    private int failedAttemptCount;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "setting_version", nullable = false)
    private long settingVersion;

    @Column(name = "requested_by", length = 255)
    private String requestedBy;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    public enum Action {
        TemporarilyLocked,
        PermanentlyLocked,
        TemporaryLockExpired,
        PermanentUnlocked
    }
}
