package com.swipeauctions.admin.controller;

import com.swipeauctions.admin.entity.AdminAuditLog;
import com.swipeauctions.admin.enums.AuditAction;
import com.swipeauctions.admin.service.AdminAuditLogService;
import com.swipeauctions.common.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

/** Read-only view of {@code AdminAuditLog} — kept separate from the already-large
 *  {@link AdminController}, same as {@code AdminStockController}/{@code AdminSettingsController}. */
@RestController
@RequestMapping("/api/admin/audit-log")
@RequiredArgsConstructor
public class AdminAuditLogController {

    private final AdminAuditLogService auditLogService;

    @GetMapping
    public PageResponse<AuditLogResponse> list(@RequestParam(required = false) AuditAction action,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1), Sort.by(Sort.Direction.DESC, "createdAt"));
        LocalDateTime fromDateTime = from != null ? from.atStartOfDay() : null;
        LocalDateTime toDateTime = to != null ? LocalDateTime.of(to, LocalTime.MAX) : null;
        return PageResponse.of(auditLogService.list(action, fromDateTime, toDateTime, pageable), AdminAuditLogController::toResponse);
    }

    private static AuditLogResponse toResponse(AdminAuditLog a) {
        return new AuditLogResponse(a.getId(), a.getAdmin().getEmail(), a.getAction(),
                a.getTargetType(), a.getTargetId(), a.getSummary(), a.getCreatedAt());
    }

    public record AuditLogResponse(UUID id, String adminEmail, AuditAction action,
                                    String targetType, String targetId, String summary, LocalDateTime createdAt) {}
}
