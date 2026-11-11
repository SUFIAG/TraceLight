package com.sentinel.common.exception;

/**
 * The caller is authenticated and authorised, but their second factor is too
 * old for the action they asked for.
 *
 * <p>Distinct from a role denial so the console can offer the right remedy:
 * "confirm your identity" is actionable, "you do not have permission" is not.
 */
public class StepUpRequiredException extends RuntimeException {

    public StepUpRequiredException(String message) {
        super(message);
    }
}
