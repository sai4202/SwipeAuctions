package com.swipeauctions.user.dtos;

import com.swipeauctions.enums.KycStatus;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KycStatusResponseDTO {

    private Boolean kycCompleted;

    private KycStatus status;

    private String fullName;

    private LocalDateTime submittedAt;

    private LocalDateTime verifiedAt;

    private String remarks;
}
