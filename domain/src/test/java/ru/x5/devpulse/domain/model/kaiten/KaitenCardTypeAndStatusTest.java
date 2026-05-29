package ru.x5.devpulse.domain.model.kaiten;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;

@DisplayName("KaitenCardType / KaitenColumnStatus: маппинг id ↔ enum")
class KaitenCardTypeAndStatusTest {

    @ParameterizedTest(name = "[{index}] type_id={0} → {1}")
    @CsvSource({
            "70, DEVELOPMENT",
            "8,  DEFECT",
            "999, OTHER",
            "-1, OTHER"
    })
    @DisplayName("KaitenCardType.fromId: известные id мапятся, неизвестные → OTHER")
    void cardTypeMatrix(int id, KaitenCardType expected) {
        assertThat(KaitenCardType.fromId(id)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "[{index}] null → OTHER")
    @NullSource
    @DisplayName("KaitenCardType.fromId(null) → OTHER")
    void nullCardType(Integer id) {
        assertThat(KaitenCardType.fromId(id)).isEqualTo(KaitenCardType.OTHER);
    }

    @ParameterizedTest(name = "[{index}] column.type={0} → {1}")
    @CsvSource({
            "1, NEW",
            "2, IN_PROGRESS",
            "3, DONE",
            "999, UNKNOWN",
            "0, UNKNOWN"
    })
    @DisplayName("KaitenColumnStatus.fromType: 1/2/3 → NEW/IN_PROGRESS/DONE, остальное → UNKNOWN")
    void columnStatusMatrix(int type, KaitenColumnStatus expected) {
        assertThat(KaitenColumnStatus.fromType(type)).isEqualTo(expected);
    }
}
