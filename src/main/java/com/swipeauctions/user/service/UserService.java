package com.swipeauctions.user.service;

import com.swipeauctions.user.dtos.*;

public interface UserService {

    String requestEmailChange(
            ChangeEmailRequestDTO request,
            String email
    );

    String verifyEmailChange(
            VerifyEmailChangeDTO request,
            String email
    );

    String requestMobileChange(
            ChangeMobileRequestDTO request,
            String email
    );

    String verifyMobileChange(
            VerifyMobileChangeDTO request,
            String email
    );

    String resendEmailChangeOtp(String email);

    String resendMobileChangeOtp(String email);

    UserProfileResponseDTO getProfile(
            String email
    );
}
