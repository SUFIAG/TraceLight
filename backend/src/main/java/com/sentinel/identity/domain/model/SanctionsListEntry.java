package com.sentinel.identity.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * A row from a sanctions, PEP, or watchlist feed. Held locally so screening
 * stays inside the latency budget of an onboarding flow and so a decision can
 * be reproduced against the list as it stood on the day it was made.
 */
@Entity
@Table(name = "sanctions_list_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SanctionsListEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** SANCTIONS, PEP, ADVERSE_MEDIA, WATCHLIST. */
    @Column(name = "list_type", nullable = false, length = 30)
    private String listType;

    @Column(name = "list_name", nullable = false)
    private String listName;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    /** Pre-normalised for matching: lower case, punctuation stripped, tokens sorted. */
    @Column(name = "normalised_name", nullable = false)
    private String normalisedName;

    @Column(name = "date_of_birth", length = 20)
    private String dateOfBirth;

    @Column(length = 2)
    private String country;

    private String program;

    @Column(nullable = false)
    private boolean active;
}
