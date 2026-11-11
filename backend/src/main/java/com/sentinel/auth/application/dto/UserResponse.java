package com.sentinel.auth.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sentinel.auth.domain.model.User;
import com.sentinel.security.domain.model.UserSecurity;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * A user as the console sees them.
 *
 * <p>Security posture is included because the settings screen and the team
 * list both need it, but only ever as booleans and masked contact details. A
 * TOTP secret, a code hash, or a full phone number has no business leaving the
 * backend.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserResponse {

    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
    private String role;
    private boolean enabled;
    private Instant lastLogin;
    private Instant createdAt;
    private String jobTitle;
    private String timezone;
    /** Set when the user has a picture; the console fetches it by user id. */
    private Instant avatarUpdatedAt;

    /* ---------- Security posture ---------- */

    private Boolean mfaEnabled;
    private String mfaMethod;
    private Boolean emailVerified;
    private Boolean phoneVerified;
    /** Masked, for example {@code ••••••89}. Never the full number. */
    private String phoneNumber;
    private Boolean locked;
    private Instant lockedUntil;
    private Boolean mustChangePassword;
    private Instant passwordChangedAt;

    public static UserResponse from(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole().name())
                .enabled(user.isEnabled())
                .lastLogin(user.getLastLogin())
                .createdAt(user.getCreatedAt())
                .jobTitle(user.getJobTitle())
                .timezone(user.getTimezone())
                .avatarUpdatedAt(user.getAvatarUpdatedAt())
                .build();
    }

    /** The same user, with the parts of their security posture that are safe to show. */
    public static UserResponse from(User user, UserSecurity security) {
        UserResponse response = from(user);
        if (security == null) return response;
        response.setMfaEnabled(security.isMfaEnabled());
        response.setMfaMethod(security.getMfaMethod() == null ? null : security.getMfaMethod().name());
        response.setEmailVerified(security.isEmailVerified());
        response.setPhoneVerified(security.isPhoneVerified());
        response.setPhoneNumber(security.getPhoneNumber() == null
                ? null : com.sentinel.security.application.service.Secrets.maskPhone(security.getPhoneNumber()));
        response.setLocked(security.isLocked());
        response.setLockedUntil(security.isLocked() ? security.getLockedUntil() : null);
        response.setMustChangePassword(security.isMustChangePassword());
        response.setPasswordChangedAt(security.getPasswordChangedAt());
        return response;
    }
}
