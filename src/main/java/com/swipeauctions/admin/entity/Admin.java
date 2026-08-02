package com.swipeauctions.admin.entity;

import jakarta.persistence.*;
import lombok.*;
import com.swipeauctions.admin.enums.AdminRole;
import com.swipeauctions.common.entity.BaseEntity;
import com.swipeauctions.enums.Role;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "admins",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "email"),
                @UniqueConstraint(columnNames = "mobile_number")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Admin extends BaseEntity {

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(
            name = "mobile_number",
            nullable = false,
            unique = true
    )
    private String mobileNumber;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Role role = Role.ADMIN;

    // Permission tier within admin accounts — see AdminRole's javadoc.
    @Enumerated(EnumType.STRING)
    @Column(name = "admin_role", nullable = false)
    @Builder.Default
    private AdminRole adminRole = AdminRole.ADMIN;

    @Builder.Default
    private Boolean active = true;

    // Security Fields

    @Builder.Default
    private Boolean accountNonLocked = true;

    @Builder.Default
    private Integer failedLoginAttempts = 0;

    private LocalDateTime lockedUntil;

    // Audit Fields

    private LocalDateTime lastLoginAt;

    private String lastLoginIp;

    private String lastLoginDevice;

    private LocalDateTime passwordChangedAt;
}