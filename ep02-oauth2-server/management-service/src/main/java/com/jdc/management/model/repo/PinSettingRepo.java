package com.jdc.management.model.repo;

import java.util.List;

import com.jdc.management.model.BaseRepository;
import com.jdc.management.model.entity.PinLockSetting;

public interface PinSettingRepo extends BaseRepository<PinLockSetting, String> {
    List<PinLockSetting> findByDeletedFalseOrderBySystemAsc();
}
