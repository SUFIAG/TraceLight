package com.sentinel.profile.adapter.in.rest;

import com.sentinel.auth.application.dto.UserResponse;
import com.sentinel.common.dto.ApiResponse;
import com.sentinel.profile.application.service.MyProfileService;
import com.sentinel.profile.domain.model.UserAvatar;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * The signed-in user's own profile.
 *
 * <p>Separate from {@code /api/v1/profiles}, which holds behavioural customer
 * profiles — a different thing entirely, and one an analyst should never
 * confuse with their own account page.
 */
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class MyProfileController {

    private final MyProfileService profileService;

    @PatchMapping
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(
            @RequestBody Map<String, String> body, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(profileService.updateProfile(
                userId(auth),
                body.get("firstName"),
                body.get("lastName"),
                body.get("jobTitle"),
                body.get("timezone"))));
    }

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Map<String, Object>>> uploadAvatar(
            @RequestPart("file") MultipartFile file, Authentication auth) {
        Instant updatedAt = profileService.uploadAvatar(userId(auth), file);
        return ResponseEntity.ok(ApiResponse.success(Map.of("updatedAt", updatedAt)));
    }

    /**
     * Serves a picture. Requires a token like every other endpoint: an avatar
     * reveals who works at the organization, which is not public.
     */
    @GetMapping("/avatar/{targetUserId}")
    public ResponseEntity<byte[]> avatar(@PathVariable UUID targetUserId, Authentication auth) {
        return profileService.avatar(targetUserId, orgId(auth))
                .map(this::imageResponse)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/avatar")
    public ResponseEntity<ApiResponse<Map<String, Object>>> removeAvatar(Authentication auth) {
        profileService.removeAvatar(userId(auth));
        return ResponseEntity.ok(ApiResponse.success(Map.of("removed", true)));
    }

    private ResponseEntity<byte[]> imageResponse(UserAvatar avatar) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                // Private: the image is personal data, so a shared cache must
                // not keep it, but the user's own browser may.
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .header(HttpHeaders.ETAG, "\"" + avatar.getUpdatedAt().toEpochMilli() + "\"")
                .body(avatar.getImageBytes());
    }

    private UUID userId(Authentication auth) {
        return UUID.fromString((String) auth.getPrincipal());
    }

    private UUID orgId(Authentication auth) {
        return UUID.fromString((String) auth.getCredentials());
    }
}
