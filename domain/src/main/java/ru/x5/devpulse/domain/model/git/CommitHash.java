package ru.x5.devpulse.domain.model.git;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * SHA-1/SHA-256 хеш git-коммита.
 *
 * <p>Принимает оба варианта (40 или 64 hex-символов).
 * Нижний регистр обязателен — нормализуем для уникальности ключа.</p>
 */
public record CommitHash(String value) {

    private static final Pattern HEX = Pattern.compile("[0-9a-f]+");

    public CommitHash {
        Objects.requireNonNull(value, "commit hash must not be null");
        String normalized = value.trim().toLowerCase();
        if (normalized.length() != 40 && normalized.length() != 64) {
            throw new IllegalArgumentException(
                    "commit hash must be 40 (SHA-1) or 64 (SHA-256) chars, got " + normalized.length());
        }
        if (!HEX.matcher(normalized).matches()) {
            throw new IllegalArgumentException("commit hash must be hex, got: " + value);
        }
        value = normalized;
    }

    /** Короткая форма для логов/UI: первые 7 символов. */
    public String shortValue() {
        return value.substring(0, 7);
    }

    @Override
    public String toString() {
        return value;
    }
}
