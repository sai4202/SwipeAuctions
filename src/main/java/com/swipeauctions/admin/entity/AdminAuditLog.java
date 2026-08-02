package com.swipeauctions.admin.entity;

import com.swipeauctions.admin.enums.AuditAction;
import com.swipeauctions.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/** One row per mutating admin action — who did it, what kind, on what, and a human-readable
 *  one-liner built at the call site (where all the context is already at hand). */
@Entity
@Table(name = "admin_audit_log", indexes = {
        @Index(name = "idx_admin_audit_log_admin", columnList = "admin_id"),
        @Index(name = "idx_admin_audit_log_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminAuditLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_id", nullable = false)
    private Admin admin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditAction action;

    /** e.g. "User", "Auction", "Listing", "Dispute", "Category", "Kyc", "Settings", "Stock". */
    @Column(nullable = false)
    private String targetType;

    /** Nullable — some actions (e.g. a bulk import) don't have a single target row. */
    private String targetId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;
}
