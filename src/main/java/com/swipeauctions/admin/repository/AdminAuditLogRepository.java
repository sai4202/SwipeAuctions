package com.swipeauctions.admin.repository;

import com.swipeauctions.admin.entity.AdminAuditLog;
import com.swipeauctions.admin.enums.AuditAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.UUID;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, UUID> {

    @Query("select a from AdminAuditLog a where (cast(:action as string) is null or a.action = :action) "
            + "and (cast(:from as timestamp) is null or a.createdAt >= :from) "
            + "and (cast(:to as timestamp) is null or a.createdAt <= :to)")
    Page<AdminAuditLog> search(@Param("action") AuditAction action, @Param("from") LocalDateTime from,
                                @Param("to") LocalDateTime to, Pageable pageable);
}
