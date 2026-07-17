package com.swipeauctions.common.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import com.swipeauctions.admin.repository.AdminRepository;
import com.swipeauctions.user.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService
{
    private final UserRepository userRepository;

    private final AdminRepository adminRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {

        // Check User table first.
        return userRepository.findByEmail(email)

                .map(user -> org.springframework.security.core.userdetails.User
                                .builder()
                                .username(user.getEmail())
                                .password(user.getPassword())
                                .authorities("ROLE_" + user.getRole().name())
                                .build()
                )

                // If user not found, search Admin table.
                .orElseGet(() -> adminRepository.findByEmail(email)
                        .map(admin -> org.springframework.security.core.userdetails.User
                                .builder()
                                .username(admin.getEmail())
                                .password(admin.getPassword())
                                .authorities("ROLE_" + admin.getRole().name())
                                .build()
                        ).orElseThrow(() -> new UsernameNotFoundException("User/Admin not found")));
    }
}
