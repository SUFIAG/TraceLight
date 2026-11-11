package com.sentinel.common.util;

import com.sentinel.common.exception.BusinessException;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Parses client-supplied strings into enum constants.
 *
 * <p>{@code Enum.valueOf} throws {@link IllegalArgumentException}, which reaches
 * the generic handler and becomes a 500 with the message "An unexpected error
 * occurred". A caller sending {@code IN_PROGRESS} where the enum defines
 * {@code INVESTIGATING} has made a request error, not triggered a server fault,
 * and deserves to be told which values are valid.
 */
public final class Enums {

    private Enums() {
    }

    public static <E extends Enum<E>> E parse(Class<E> type, String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("INVALID_" + fieldName.toUpperCase(),
                    fieldName + " is required. Valid values: " + permitted(type));
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("INVALID_" + fieldName.toUpperCase(),
                    "'" + value + "' is not a valid " + fieldName + ". Valid values: " + permitted(type));
        }
    }

    /** Null-and-blank tolerant variant for optional query parameters. */
    public static <E extends Enum<E>> E parseOrNull(Class<E> type, String value, String fieldName) {
        return (value == null || value.isBlank()) ? null : parse(type, value, fieldName);
    }

    private static <E extends Enum<E>> String permitted(Class<E> type) {
        return Arrays.stream(type.getEnumConstants())
                .map(Enum::name)
                .collect(Collectors.joining(", "));
    }
}
