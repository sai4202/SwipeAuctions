package com.swipeauctions.user.dtos;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponseDTO {

    private UUID id;

    private String userReferenceNumber;

    private String email;

    private String mobileNumber;

    private String role;

    private Boolean active;

    private Boolean emailVerified;

    private Boolean mobileVerified;

    private Boolean kycCompleted;

}
