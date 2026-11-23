package com.pfe.marketplace.settings.repository;

import com.pfe.marketplace.settings.entity.PlatformSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformSettingRepository extends JpaRepository<PlatformSetting, String> {
}
