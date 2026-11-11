package com.sentinel.consortium.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * A shared risk signal, stored only as a keyed hash.
 *
 * <p>The consortium is the part of the platform that gets stronger with every
 * client, which makes it the moat — and also the part most likely to be
 * refused by a bank's legal team. Both facts shape the design: no raw
 * identifier is ever stored, no contributor is named, and a signal is only
 * returned once more than one institution has reported it.
 *
 * <p>Hashing is HMAC-SHA256 under a platform pepper, not a plain digest.
 * A plain hash of an account number or a phone number is trivially reversed
 * by enumerating the space, which would make this table a directory of every
 * identifier any client has ever seen.
 */
@Entity
@Table(name = "consortium_signals", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"hashed_identifier", "signal_type"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsortiumSignal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** HMAC-SHA256 of the identifier under the platform pepper. */
    @Column(name = "hashed_identifier", nullable = false, length = 64)
    private String hashedIdentifier;

    /** ACCOUNT, DEVICE, EMAIL, PHONE, IBAN, CARD_BIN, WALLET, DOMAIN. */
    @Column(name = "identifier_type", nullable = false, length = 20)
    private String identifierType;

    /** MULE_ACCOUNT, SCAM_BENEFICIARY, COMPROMISED_DEVICE, SYNTHETIC_IDENTITY,
     *  CARD_TESTING, ACCOUNT_TAKEOVER, PHISHING_INFRASTRUCTURE. */
    @Column(name = "signal_type", nullable = false, length = 40)
    private String signalType;

    /** 0-100, the mean severity across contributors. */
    @Column(nullable = false)
    private int severity;

    /**
     * How many distinct institutions reported this. The k-anonymity gate: a
     * signal from one contributor is that contributor's private intelligence
     * and is not shared back out.
     */
    @Column(name = "contributor_count", nullable = false)
    private int contributorCount;

    /** Total reports, including repeats from the same contributor. */
    @Column(name = "sighting_count", nullable = false)
    private int sightingCount;

    @Column(name = "first_seen", nullable = false)
    private Instant firstSeen;

    @Column(name = "last_seen", nullable = false)
    private Instant lastSeen;

    /** Signals decay. A device compromised two years ago is not evidence today. */
    @Column(name = "expires_at")
    private Instant expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
