package com.sentinel.profile.application.service;

import com.sentinel.auth.adapter.out.persistence.UserRepository;
import com.sentinel.auth.application.dto.UserResponse;
import com.sentinel.auth.domain.model.User;
import com.sentinel.common.exception.BusinessException;
import com.sentinel.common.exception.ResourceNotFoundException;
import com.sentinel.profile.adapter.out.persistence.UserAvatarRepository;
import com.sentinel.profile.domain.model.UserAvatar;
import com.sentinel.security.application.service.UserSecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The user's own profile: name, job title, timezone, and picture.
 *
 * <p>Image upload is the one place in this product where a user hands the
 * server an arbitrary file, so it is treated as hostile. The declared content
 * type is ignored — anyone can claim {@code image/png} — and the file is
 * instead decoded by the image library. A file that does not decode is not an
 * image, whatever its name says. What is stored is the re-encoded output, not
 * the upload, which drops EXIF, trailing archive data, and the SVG-with-script
 * trick in one step.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MyProfileService {

    /** 2 MB before decoding. A 512-pixel avatar needs a fraction of it. */
    private static final long MAX_UPLOAD_BYTES = 2L * 1024 * 1024;

    /** Largest edge after resizing. Avatars render at 40px; 512 covers retina. */
    private static final int MAX_DIMENSION = 512;

    /** Guard against a decompression bomb: a small file claiming a huge canvas. */
    private static final long MAX_PIXELS = 40_000_000L;

    private static final Set<String> ACCEPTED_TYPES =
            Set.of("image/png", "image/jpeg", "image/jpg", "image/webp", "image/gif");

    private final UserRepository userRepository;
    private final UserAvatarRepository avatarRepository;
    private final UserSecurityService userSecurityService;

    @Transactional
    public UserResponse updateProfile(UUID userId, String firstName, String lastName,
                                      String jobTitle, String timezone) {
        User user = require(userId);
        if (firstName != null) user.setFirstName(trimmed(firstName, 100, "First name"));
        if (lastName != null) user.setLastName(trimmed(lastName, 100, "Last name"));
        if (jobTitle != null) user.setJobTitle(trimmed(jobTitle, 120, "Job title"));
        if (timezone != null) user.setTimezone(trimmed(timezone, 64, "Timezone"));
        userRepository.save(user);
        return UserResponse.from(user, userSecurityService.forUser(user));
    }

    /**
     * Accepts an image, normalises it, and replaces any existing picture.
     *
     * @return when the avatar was updated, which the console uses to bust its cache
     */
    @Transactional
    public Instant uploadAvatar(UUID userId, MultipartFile file) {
        User user = require(userId);

        if (file == null || file.isEmpty()) {
            throw new BusinessException("NO_FILE", "Choose an image to upload");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new BusinessException("FILE_TOO_LARGE", "Images must be smaller than 2 MB");
        }
        if (file.getContentType() != null
                && !ACCEPTED_TYPES.contains(file.getContentType().toLowerCase())) {
            throw new BusinessException("UNSUPPORTED_IMAGE",
                    "Use a PNG, JPEG, WebP, or GIF image");
        }

        BufferedImage decoded;
        try {
            decoded = ImageIO.read(file.getInputStream());
        } catch (IOException e) {
            throw new BusinessException("UNREADABLE_IMAGE", "That file could not be read as an image");
        }
        if (decoded == null) {
            // Reached by anything that is not an image the platform can decode,
            // including a script renamed to .png.
            throw new BusinessException("UNSUPPORTED_IMAGE",
                    "That file is not an image the platform can read");
        }
        if ((long) decoded.getWidth() * decoded.getHeight() > MAX_PIXELS) {
            throw new BusinessException("IMAGE_TOO_LARGE", "That image has too many pixels");
        }

        byte[] normalised = toSquarePng(decoded);

        avatarRepository.save(UserAvatar.builder()
                .userId(userId)
                .contentType("image/png")
                .imageBytes(normalised)
                .sizeBytes(normalised.length)
                .updatedAt(Instant.now())
                .build());

        user.setAvatarUpdatedAt(Instant.now());
        userRepository.save(user);
        return user.getAvatarUpdatedAt();
    }

    @Transactional(readOnly = true)
    public Optional<UserAvatar> avatar(UUID userId, UUID organizationId) {
        // Scoped to the caller's organization: a profile picture is personal
        // data, and an identifier from another tenant must not resolve.
        boolean sameOrganization = userRepository.findById(userId)
                .map(user -> user.getOrganizationId().equals(organizationId))
                .orElse(false);
        return sameOrganization ? avatarRepository.findById(userId) : Optional.empty();
    }

    @Transactional
    public void removeAvatar(UUID userId) {
        avatarRepository.deleteById(userId);
        userRepository.findById(userId).ifPresent(user -> {
            user.setAvatarUpdatedAt(null);
            userRepository.save(user);
        });
    }

    /* ---------- Internals ---------- */

    /**
     * Centre-crops to a square and scales to at most {@link #MAX_DIMENSION},
     * then writes PNG. Cropping server-side means every avatar in the console
     * is the same shape, so the UI never has to deal with a 3:1 banner.
     */
    private byte[] toSquarePng(BufferedImage source) {
        int edge = Math.min(source.getWidth(), source.getHeight());
        int x = (source.getWidth() - edge) / 2;
        int y = (source.getHeight() - edge) / 2;
        BufferedImage cropped = source.getSubimage(x, y, edge, edge);

        int target = Math.min(edge, MAX_DIMENSION);
        BufferedImage output = new BufferedImage(target, target, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = output.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.drawImage(cropped, 0, 0, target, target, null);
        graphics.dispose();

        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            ImageIO.write(output, "png", buffer);
            return buffer.toByteArray();
        } catch (IOException e) {
            throw new BusinessException("IMAGE_WRITE_FAILED", "That image could not be processed");
        }
    }

    private String trimmed(String value, int max, String label) {
        String clean = value.trim();
        if (clean.length() > max) {
            throw new BusinessException("FIELD_TOO_LONG", label + " must be " + max + " characters or fewer");
        }
        return clean.isEmpty() ? null : clean;
    }

    private User require(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));
    }
}
