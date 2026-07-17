package com.swipeauctions.session.entity;

import jakarta.persistence.*;
import lombok.*;
import com.swipeauctions.admin.entity.Admin;
import com.swipeauctions.common.entity.BaseEntity;

import java.time.LocalDateTime;

@Entity
@Table(name = "admin_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminSessions extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id", nullable = false)
    private Admin admin;

    @Column(nullable = false, unique = true)
    private String jwtId;

    @Column(nullable = false)
    private String deviceId;

    private String deviceName;

    private String ipAddress;

    @Column(length = 1000)
    private String userAgent;

    @Builder.Default
    private Boolean active = true;

    private LocalDateTime loginTime;

    private LocalDateTime lastActivityTime;

    private LocalDateTime logoutTime;

}
