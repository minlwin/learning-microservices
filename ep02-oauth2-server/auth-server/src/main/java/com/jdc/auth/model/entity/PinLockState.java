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
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "pin_lock_state")
public class PinLockState {

    @Id
    private UUID profileId;

    @MapsId
    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id")
    private Profile profile;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Status status;

    @Column(name = "failed_attempt_count", nullable = false)
    private int failedAttemptCount;

    @Column(name = "failed_since_temporary_lock", nullable = false)
    private int failedSinceTemporaryLock;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_failed_at")
    private Instant lastFailedAt;

    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    @Column(name = "setting_version", nullable = false)
    private long settingVersion;

    @Version
    @Column(nullable = false)
    private long version;

    public enum Status {
        Active,
        TemporarilyLocked,
        PermanentlyLocked
    }
}
