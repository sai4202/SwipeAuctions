package com.swipeauctions.yard.entity;

import jakarta.persistence.*;
import lombok.*;
import com.swipeauctions.auctions.entity.AuctionItem;
import com.swipeauctions.common.entity.BaseEntity;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "yards",
        indexes = {
                @Index(name = "idx_yard_code", columnList = "yard_code"),
                @Index(name = "idx_yard_city", columnList = "city"),
                @Index(name = "idx_yard_state", columnList = "state")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Yard extends BaseEntity {

    @Column(
            name = "yard_code",
            nullable = false,
            unique = true,
            length = 20
    )
    private String yardCode;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 300)
    private String address;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(nullable = false, length = 100)
    private String state;

    @Column(nullable = false, length = 10)
    private String pincode;

    @Column(name = "contact_number", nullable = false, length = 15)
    private String contactNumber;

    @Column(length = 150)
    private String email;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    @Builder.Default
    @OneToMany(
            mappedBy = "yard",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    private List<ContactPerson> contactPersons = new ArrayList<>();

    @Builder.Default
    @OneToMany(
            mappedBy = "yard",
           cascade = CascadeType.ALL,
           orphanRemoval = true,
           fetch = FetchType.LAZY
   )
   private List<AuctionItem> auctionItems = new ArrayList<>();
}