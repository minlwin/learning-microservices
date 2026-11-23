package com.jdc.auth.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;

@Data
@Entity
public class PinLockSetting {

    @Id
    private String system;

    @Column(name = "temp_lock_fails_count", nullable = false)
    private int tempLockFailsCount;

    @Column(name = "temp_lock_wait_minutes", nullable = false)
    private int tempLockWaitMinutes;

    @Column(name = "permanent_lock_fails_count", nullable = false)
    private int permanentLockFailsCount;

    @Column(nullable = false)
    private boolean deleted;

    @Column(nullable = false)
    private long version;
}
