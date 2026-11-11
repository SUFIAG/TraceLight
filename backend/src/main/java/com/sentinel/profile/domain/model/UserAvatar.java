package com.sentinel.profile.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * A user's profile picture, after the backend has re-encoded it.
 *
 * <p>The bytes here are never the bytes that were uploaded. Every image is
 * decoded, resized, and written out again as PNG, which is the cheapest
 * reliable way to strip an image-shaped file of anything that is not an image:
 * EXIF payloads, appended archives, and SVG markup all fail to survive a
 * decode-and-re-encode.
 */
@Entity
@Table(name = "user_avatars")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAvatar {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    // Not @Lob: on PostgreSQL that maps to a large-object OID, and the column
    // is a plain bytea. Hibernate then writes the OID as a bigint and the
    // insert fails on a type mismatch.
    @Column(name = "image_bytes", nullable = false, columnDefinition = "bytea")
    private byte[] imageBytes;

    @Column(name = "size_bytes", nullable = false)
    private int sizeBytes;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
