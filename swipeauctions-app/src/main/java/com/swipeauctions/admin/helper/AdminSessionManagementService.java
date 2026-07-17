package com.swipeauctions.admin.helper;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.swipeauctions.admin.entity.Admin;
import com.swipeauctions.common.exception.BadRequestException;
import com.swipeauctions.common.util.DeviceUtils;
import com.swipeauctions.session.entity.AdminSessions;
import com.swipeauctions.session.repository.AdminSessionRepository;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminSessionManagementService {

    private final AdminSessionRepository adminSessionRepository;

    //Validates active admin session limit.
    public void validateAdminSessionLimit(Admin admin)
    {

        long activeSessions = adminSessionRepository.countByAdminAndActiveTrue(admin);

        // Restrict admin to a single device login.
        if (activeSessions >= 1)
        {
            throw new BadRequestException("Admin already logged in on another device");
        }
    }

    // Updates admin login audit information.

    public void updateAdminLoginAudit(Admin admin, HttpServletRequest request)
    {
        // Update latest login timestamp.
        admin.setLastLoginAt(LocalDateTime.now());

        // Update latest login IP.
        admin.setLastLoginIp(request.getRemoteAddr());

        // Update latest login device.
        admin.setLastLoginDevice(DeviceUtils.extractDeviceName(request.getHeader("User-Agent")));

    }

    //Creates a new admin session.
    public void createAdminSession(Admin admin, HttpServletRequest request, String jwtId)
    {

        // Extract device information.
        String userAgent = request.getHeader("User-Agent");

        if (userAgent == null)
        {
            userAgent = "Unknown Device";
        }

        AdminSessions adminSession = AdminSessions.builder()

                // Associate admin with session.
                .admin(admin)

                // Persist JWT identifier.
                .jwtId(jwtId)

                // Generate unique device identifier.
                .deviceId(UUID.randomUUID().toString())

                // Store device information.
                .deviceName(DeviceUtils.extractDeviceName(userAgent))

                // Store client IP address.
                .ipAddress(request.getRemoteAddr())

                // Persist raw user-agent.
                .userAgent(userAgent)

                // Mark session as active.
                .active(true)

                // Track login timestamp.
                .loginTime(LocalDateTime.now())

                // Track latest activity timestamp.
                .lastActivityTime(LocalDateTime.now())

                .build();

        adminSessionRepository.save(adminSession);
    }
}
