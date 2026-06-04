package ru.x5.devpulse.adapter.reviews;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("ReviewGatewayAdapter.toProjectPath")
class ReviewGatewayAdapterTest {

    @ParameterizedTest(name = "[{index}] {0} → {1}")
    @CsvSource({
            "https://scm.x5.ru/gkr/xrg-core.git, gkr/xrg-core",
            "https://scm.x5.ru/gkr/xrg-markable.git, gkr/xrg-markable",
            "https://scm.x5.ru/group/sub/repo.git, group/sub/repo",
            "https://scm.x5.ru/gkr/xrg-core, gkr/xrg-core"
    })
    @DisplayName("Clone-URL → namespace/repo (без схемы/.git)")
    void derivesProjectPath(String url, String expected) {
        assertThat(ReviewGatewayAdapter.toProjectPath(url)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "[{index}] пустое: \"{0}\"")
    @ValueSource(strings = {"", "   "})
    @DisplayName("Пустой/пробельный URL → null")
    void blankUrlReturnsNull(String url) {
        assertThat(ReviewGatewayAdapter.toProjectPath(url)).isNull();
    }

    @Test
    @DisplayName("null → null")
    void nullUrlReturnsNull() {
        assertThat(ReviewGatewayAdapter.toProjectPath(null)).isNull();
    }
}
