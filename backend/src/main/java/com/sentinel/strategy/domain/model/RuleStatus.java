package com.sentinel.strategy.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Where a rule sits in its lifecycle. SHADOW is the important one: a rule runs
 * against live traffic and records what it <em>would</em> have done, without
 * blocking anybody. Nothing reaches production without a shadow period long
 * enough to see its false-positive cost.
 */
@Getter
@RequiredArgsConstructor
public enum RuleStatus {

    DRAFT("Not evaluated against anything", false),
    SHADOW("Evaluated on live traffic, decisions recorded but not applied", true),
    CHALLENGER("Live on a share of traffic, competing with the champion", true),
    CHAMPION("Live on all traffic", true),
    RETIRED("No longer evaluated", false);

    private final String description;
    private final boolean evaluated;
}
