package com.sentinel.common.config;

import java.time.Instant;
import java.util.UUID;

/**
 * What the token said about this request, beyond who is making it.
 *
 * <p>Carried as the authentication's details so the existing controller
 * contract is untouched: principal stays the user id and credentials stay the
 * organization id, which is what every controller in the platform already
 * reads.
 *
 * @param sessionId    the device session the token belongs to, if any
 * @param mfaSatisfied whether a second factor was used to obtain this token
 * @param mfaAt        when that factor was last satisfied, for step-up checks
 * @param email        the signed-in address, for audit without a lookup
 */
public record AuthenticationDetails(UUID sessionId, boolean mfaSatisfied, Instant mfaAt, String email) {
}
