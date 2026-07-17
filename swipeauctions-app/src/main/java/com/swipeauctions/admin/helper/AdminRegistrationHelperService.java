package com.swipeauctions.admin.helper;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.swipeauctions.admin.dtos.AdminRegisterRequestDTO;
import com.swipeauctions.admin.entity.Admin;
import com.swipeauctions.admin.repository.AdminRepository;
import com.swipeauctions.common.exception.BadRequestException;
import com.swipeauctions.enums.Role;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AdminRegistrationHelperService {

    private final AdminRepository adminRepository;

    private final PasswordEncoder passwordEncoder;


    //Validates admin registration request.
    public void validateAdminRegistration(AdminRegisterRequestDTO request) {

        // Validate password confirmation.
        if (!request.getPassword().equals(request.getConfirmPassword())) {

            throw new BadRequestException("Password and Confirm Password do not match");
        }
        String email = request.getEmail().trim().toLowerCase();

        // Prevent duplicate email registration.
        if (adminRepository.existsByEmail(email))
        {
            throw new BadRequestException("Email already registered");
        }

        // Prevent duplicate mobile registration.
        if (adminRepository.existsByMobileNumber(request.getMobileNumber().trim()))
        {
            throw new BadRequestException("Mobile number already registered");
        }
    }

    //Creates admin entity from request.
    public Admin createAdmin(AdminRegisterRequestDTO request)
    {

        return Admin.builder()

                // Store normalized first name.
                .firstName(request.getFirstName().trim())

                // Store normalized last name.
                .lastName(request.getLastName().trim())

                // Store normalized email.
                .email(request.getEmail().trim().toLowerCase())

                // Store sanitized mobile number.
                .mobileNumber(request.getMobileNumber().trim())

                // Store encrypted password.
                .password(passwordEncoder.encode(request.getPassword()))

                // Assign admin role.
                .role(Role.ADMIN)

                // Enable admin account by default.
                .active(true)

                // Keep account unlocked initially.
                .accountNonLocked(true)

                // Initialize failed login attempts.
                .failedLoginAttempts(0)

                // Track password creation timestamp.
                .passwordChangedAt(LocalDateTime.now())
                .build();
    }
}
