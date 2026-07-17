package com.swipeauctions.yard.entity;

import jakarta.persistence.*;
import lombok.*;
import com.swipeauctions.common.entity.BaseEntity;

@Entity
@Table(
        name = "contact_persons",
        indexes = {
                @Index(name = "idx_contact_mobile", columnList = "mobile_number")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContactPerson extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "yard_id",
            nullable = false
    )
    private Yard yard;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "mobile_number", nullable = false, length = 15)
    private String mobileNumber;

    @Column(length = 150)
    private String email;

    @Column(nullable = false, length = 100)
    private String designation;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;
}