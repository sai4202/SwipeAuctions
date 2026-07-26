package com.swipeauctions.user.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.swipeauctions.common.response.ApiResponse;
import com.swipeauctions.user.dtos.KycStatusResponseDTO;
import com.swipeauctions.user.dtos.KycSubmitRequestDTO;
import com.swipeauctions.user.service.KycService;

/** Self-service KYC: required before a customer/dealer may register to bid on an auction. */
@RestController
@RequestMapping("/api/users/kyc")
@RequiredArgsConstructor
public class KycController {

    private final KycService kycService;

    @GetMapping
    public ApiResponse<KycStatusResponseDTO> getStatus(Authentication authentication)
    {
        return ApiResponse.success("KYC status fetched successfully",
                kycService.getStatus(authentication.getName()));
    }

    @PostMapping
    public ApiResponse<KycStatusResponseDTO> submit(
            @Valid
            @RequestBody KycSubmitRequestDTO request,

            Authentication authentication
    ) {
        return ApiResponse.success("KYC details submitted successfully",
                kycService.submitKyc(request, authentication.getName()));
    }
}
