package ru.x5.devpulse.domain.model.kaiten;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;

@DisplayName("KaitenCardType / KaitenColumnStatus / KaitenUrgency: маппинг id ↔ enum")
class KaitenCardTypeAndStatusTest {

    @ParameterizedTest(name = "[{index}] type_id={0} → {1}")
    @CsvSource({
            "70, DEVELOPMENT",
            "8,  DEFECT",
            "6,  TASK",
            "999, OTHER",
            "-1, OTHER"
    })
    @DisplayName("KaitenCardType.fromId: известные id мапятся, неизвестные → OTHER")
    void cardTypeMatrix(int id, KaitenCardType expected) {
        assertThat(KaitenCardType.fromId(id)).isEqualTo(expected);
    }

    @Test
    @DisplayName("isBuildWork: DEVELOPMENT и TASK — build-работа, DEFECT/OTHER — нет")
    void buildWork() {
        assertAll("build-work",
                () -> assertThat(KaitenCardType.DEVELOPMENT.isBuildWork()).isTrue(),
                () -> assertThat(KaitenCardType.TASK.isBuildWork()).isTrue(),
                () -> assertThat(KaitenCardType.DEFECT.isBuildWork()).isFalse(),
                () -> assertThat(KaitenCardType.OTHER.isBuildWork()).isFalse());
    }

    @ParameterizedTest(name = "[{index}] id_2561={0} → {1}")
    @CsvSource({
            "4523, CRITICAL",
            "4524, HIGH",
            "4525, MEDIUM",
            "4526, LOW",
            "999,  UNKNOWN",
            "-1,   UNKNOWN"
    })
    @DisplayName("KaitenUrgency.fromId: коды срочности мапятся точно, неизвестные → UNKNOWN")
    void urgencyMatrix(int id, KaitenUrgency expected) {
        assertThat(KaitenUrgency.fromId(id)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "[{index}] null → UNKNOWN")
    @NullSource
    @DisplayName("KaitenUrgency.fromId(null) → UNKNOWN")
    void nullUrgency(Integer id) {
        assertThat(KaitenUrgency.fromId(id)).isEqualTo(KaitenUrgency.UNKNOWN);
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
