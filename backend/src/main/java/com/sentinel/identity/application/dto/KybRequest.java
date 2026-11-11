package com.sentinel.identity.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.List;

/**
 * A business onboarding submission, including the ownership tree as declared
 * or as read out of incorporation documents.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KybRequest {

    @NotBlank
    private String legalName;

    @NotBlank
    private String registrationNumber;

    private String registrationCountry;
    /** ISO-8601 date. */
    private String incorporationDate;
    private String industryCode;
    private String registeredAddress;

    /** Businesses sharing the registered address, from the registry lookup. */
    private Integer addressCohabitants;
    private Integer directorCount;
    /** Directors who also sit on boards of already-flagged entities. */
    private Integer directorsLinkedToFlaggedEntities;
    /** The registry could not be reached or holds no record. */
    private Boolean registryUnverified;

    private List<Owner> owners;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Owner {
        /** PERSON or COMPANY. */
        private String type;
        private String name;
        private String country;
        private Double ownershipPercent;
        private Boolean pep;
        /** Nested holdings, for ownership chains that pass through other companies. */
        private List<Owner> owners;
    }
}
