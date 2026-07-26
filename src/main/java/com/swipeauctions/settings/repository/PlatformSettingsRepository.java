package com.swipeauctions.settings.repository;

import com.swipeauctions.settings.entity.PlatformSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PlatformSettingsRepository extends JpaRepository<PlatformSettings, UUID> {
}
