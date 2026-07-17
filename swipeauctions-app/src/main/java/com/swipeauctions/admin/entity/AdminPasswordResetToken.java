package com.swipeauctions.admin.entity;

import jakarta.persistence.*;
import lombok.*;
import com.swipeauctions.common.entity.BaseEntity;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "admin_password_reset_tokens"
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminPasswordResetToken extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "admin_id",
            nullable = false
    )
    private Admin admin;

    @Column(
            nullable = false,
            unique = true
    )
    private String token;

    @Column(nullable = false)
    private LocalDateTime expiryTime;

    @Builder.Default
    private Boolean used = false;
}
