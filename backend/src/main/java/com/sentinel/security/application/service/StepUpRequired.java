package com.sentinel.security.application.service;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an endpoint that a stale session may not call.
 *
 * <p>Password, role, and money-moving changes are the actions an attacker who
 * walks up to an unlocked laptop goes for. Requiring a recent second factor
 * means possession of a live session is not enough on its own.
 *
 * <p>Accounts without MFA are not blocked — that would make enabling MFA the
 * only way to use the product — but the requirement is recorded, and the
 * console nudges those users to enrol.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface StepUpRequired {
}
