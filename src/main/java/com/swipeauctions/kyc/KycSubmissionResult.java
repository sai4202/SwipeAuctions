package com.swipeauctions.kyc;

import com.swipeauctions.enums.KycStatus;

public record KycSubmissionResult(String providerReferenceId, KycStatus status, String remarks) {
}
