package com.swipeauctions.admin.service;

import com.swipeauctions.admin.entity.Admin;
import com.swipeauctions.admin.entity.AdminAuditLog;
import com.swipeauctions.admin.enums.AuditAction;
import com.swipeauctions.admin.repository.AdminAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Records "which admin did what" after a mutating admin action succeeds — see every
 *  {@code AdminController}/{@code AdminStockController}/{@code AdminSettingsController} mutating
 *  method for call sites. */
@Service
@RequiredArgsConstructor
public class AdminAuditLogService {

    private final AdminAuditLogRepository repository;

    @Transactional
    public void record(Admin admin, AuditAction action, String targetType, String targetId, String summary) {
        repository.save(AdminAuditLog.builder()
                .admin(admin).action(action).targetType(targetType).targetId(targetId).summary(summary)
                .build());
    }

    @Transactional(readOnly = true)
    public Page<AdminAuditLog> list(AuditAction action, LocalDateTime from, LocalDateTime to, Pageable pageable) {
        return repository.search(action, from, to, pageable);
    }
}
