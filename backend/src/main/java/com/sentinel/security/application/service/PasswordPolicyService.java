package com.sentinel.security.application.service;

import com.sentinel.common.exception.BusinessException;
import com.sentinel.security.domain.model.UserSecurity;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Decides whether a password is allowed, and says why when it is not.
 *
 * <p>Length does most of the work, so the floor is twelve characters rather
 * than the eight that complexity rules were invented to rescue. The rest of
 * the checks exist because real breaches are built from them: reused
 * passwords, the user's own name, keyboard runs, and the few thousand
 * passwords that appear in every credential-stuffing list.
 *
 * <p>Every failure is reported at once. A policy that reveals one problem per
 * attempt trains people to pick the first thing that squeaks through.
 */
@Service
@RequiredArgsConstructor
public class PasswordPolicyService {

    /**
     * The passwords that dominate credential-stuffing lists, plus the ones
     * people reach for when a product is called Sentinel. A full breach corpus
     * belongs behind a k-anonymity API; this list catches the common cases
     * without a network call on the sign-up path.
     */
    private static final Set<String> COMMON = Set.of(
            "password", "password1", "password123", "passw0rd", "p@ssw0rd", "p@ssword123",
            "123456", "1234567", "12345678", "123456789", "1234567890", "12345678910",
            "qwerty", "qwerty123", "qwertyuiop", "asdfghjkl", "zxcvbnm",
            "letmein", "welcome", "welcome1", "welcome123", "admin", "admin123", "administrator",
            "iloveyou", "monkey", "dragon", "sunshine", "princess", "football", "baseball",
            "abc123", "abcd1234", "trustno1", "changeme", "changeme123", "secret", "superman",
            "sentinel", "sentinel1", "sentinel123", "tracelight", "tracelight123", "fraud123");

    private static final String[] SEQUENCES = {
            "abcdefghijklmnopqrstuvwxyz", "qwertyuiop", "asdfghjkl", "zxcvbnm", "0123456789"};

    private final PasswordEncoder passwordEncoder;
    private final SecurityProperties properties;

    /**
     * Validates a candidate password.
     *
     * @param email    the account's address, so the password cannot contain it
     * @param names    first and last name, same reason
     * @param security existing posture, for the reuse check; may be null at registration
     */
    public void validate(String password, String email, List<String> names, UserSecurity security) {
        List<String> problems = new ArrayList<>();

        if (password == null || password.isBlank()) {
            throw new BusinessException("WEAK_PASSWORD", "Enter a password");
        }
        if (password.length() < properties.getMinPasswordLength()) {
            problems.add("be at least " + properties.getMinPasswordLength() + " characters");
        }
        if (password.length() > properties.getMaxPasswordLength()) {
            problems.add("be at most " + properties.getMaxPasswordLength() + " characters");
        }
        if (password.chars().noneMatch(Character::isUpperCase)) {
            problems.add("include a capital letter");
        }
        if (password.chars().noneMatch(Character::isLowerCase)) {
            problems.add("include a lower-case letter");
        }
        if (password.chars().noneMatch(Character::isDigit)) {
            problems.add("include a number");
        }
        if (password.chars().allMatch(Character::isLetterOrDigit)) {
            problems.add("include a symbol");
        }
        if (password.chars().distinct().count() < 5) {
            problems.add("use more than a handful of different characters");
        }

        String lower = password.toLowerCase(Locale.ROOT);
        if (COMMON.contains(lower) || COMMON.contains(lower.replaceAll("[^a-z0-9]", ""))) {
            problems.add("not be a password that appears in breach lists");
        }
        if (containsSequence(lower)) {
            problems.add("not be a straight run of keyboard or alphabet characters");
        }
        if (email != null && !email.isBlank()) {
            String local = email.split("@")[0].toLowerCase(Locale.ROOT);
            if (local.length() >= 3 && lower.contains(local)) {
                problems.add("not contain your email address");
            }
        }
        if (names != null) {
            for (String name : names) {
                if (name != null && name.length() >= 3 && lower.contains(name.toLowerCase(Locale.ROOT))) {
                    problems.add("not contain your name");
                    break;
                }
            }
        }

        if (!problems.isEmpty()) {
            throw new BusinessException("WEAK_PASSWORD", "Your password must " + join(problems) + ".");
        }

        if (security != null && isReused(password, security)) {
            throw new BusinessException("PASSWORD_REUSED",
                    "You have used this password before. Choose one of your last "
                            + properties.getPasswordHistorySize() + " that you have not.");
        }
    }

    /** True when the candidate matches the current password or one recently retired. */
    public boolean isReused(String password, UserSecurity security) {
        List<String> history = security.getPasswordHistory();
        if (history == null) return false;
        return history.stream().anyMatch(hash -> passwordEncoder.matches(password, hash));
    }

    /** Pushes the retired hash onto the history, keeping only the configured depth. */
    public void remember(UserSecurity security, String passwordHash) {
        List<String> history = new ArrayList<>(
                security.getPasswordHistory() == null ? List.of() : security.getPasswordHistory());
        history.add(0, passwordHash);
        while (history.size() > properties.getPasswordHistorySize()) {
            history.remove(history.size() - 1);
        }
        security.setPasswordHistory(history);
    }

    /**
     * A 0-100 score for the strength meter. Deliberately simple and explained
     * in the UI, because a score nobody can act on is decoration.
     */
    public int strength(String password) {
        if (password == null || password.isEmpty()) return 0;
        int score = Math.min(40, password.length() * 3);
        if (password.chars().anyMatch(Character::isUpperCase)) score += 10;
        if (password.chars().anyMatch(Character::isLowerCase)) score += 10;
        if (password.chars().anyMatch(Character::isDigit)) score += 10;
        if (password.chars().anyMatch(c -> !Character.isLetterOrDigit(c))) score += 15;
        score += Math.min(15, (int) password.chars().distinct().count());
        if (COMMON.contains(password.toLowerCase(Locale.ROOT))) score = Math.min(score, 10);
        if (containsSequence(password.toLowerCase(Locale.ROOT))) score -= 15;
        return Math.max(0, Math.min(100, score));
    }

    private boolean containsSequence(String lower) {
        for (String sequence : SEQUENCES) {
            for (int i = 0; i + 4 <= sequence.length(); i++) {
                String run = sequence.substring(i, i + 4);
                if (lower.contains(run) || lower.contains(new StringBuilder(run).reverse().toString())) {
                    return true;
                }
            }
        }
        return false;
    }

    private String join(List<String> problems) {
        if (problems.size() == 1) return problems.get(0);
        return String.join(", ", problems.subList(0, problems.size() - 1))
                + ", and " + problems.get(problems.size() - 1);
    }
}
