package ru.x5.markable.dev.analytics.domain.model.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CommitHashTest {

    private static final String SHA1 = "a".repeat(40);
    private static final String SHA256 = "b".repeat(64);

    @Test
    void acceptsSha1() {
        CommitHash h = new CommitHash(SHA1);
        assertThat(h.value()).isEqualTo(SHA1);
        assertThat(h.shortValue()).hasSize(7);
    }

    @Test
    void acceptsSha256() {
        assertThat(new CommitHash(SHA256).value()).isEqualTo(SHA256);
    }

    @Test
    void lowercases() {
        CommitHash h = new CommitHash("A".repeat(40));
        assertThat(h.value()).isEqualTo("a".repeat(40));
    }

    @Test
    void rejectsWrongLength() {
        assertThatThrownBy(() -> new CommitHash("abc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("40");
    }

    @Test
    void rejectsNonHex() {
        assertThatThrownBy(() -> new CommitHash("z".repeat(40)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hex");
    }
}
