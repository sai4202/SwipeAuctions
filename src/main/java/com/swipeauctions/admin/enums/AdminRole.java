package com.swipeauctions.admin.enums;

/** Permission tier for an {@code Admin} account — distinct from the platform-wide {@code Role}
 *  enum (USER/ADMIN/DEALER), which Spring Security's {@code hasRole("ADMIN")} check is keyed on.
 *  Every admin still has {@code Role.ADMIN}; this is a finer-grained tier on top of that, currently
 *  only gating who may create new admin accounts (see AdminAuthServiceImpl#register). */
public enum AdminRole {
    SUPER_ADMIN,
    ADMIN
}
