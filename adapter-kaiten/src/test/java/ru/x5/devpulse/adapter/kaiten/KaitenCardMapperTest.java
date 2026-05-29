package ru.x5.devpulse.adapter.kaiten;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import ru.x5.devpulse.adapter.kaiten.dto.KaitenCardDto;
import ru.x5.devpulse.adapter.kaiten.dto.KaitenMemberDto;
import ru.x5.devpulse.adapter.kaiten.dto.KaitenUserDto;
import ru.x5.devpulse.domain.model.kaiten.KaitenCard;

@DisplayName("KaitenCardMapper: webBaseUrl → KaitenCard.url + полные mappings")
class KaitenCardMapperTest {

    private final KaitenCardMapper mapper = Mappers.getMapper(KaitenCardMapper.class);

    @ParameterizedTest(name = "[{index}] base={0} + id={1} → {2}")
    @CsvSource({
            "https://kaiten.x5.ru,        123, https://kaiten.x5.ru/123",
            "https://kaiten.x5.ru/,       123, https://kaiten.x5.ru/123",
            "https://kaiten.x5.ru////,    123, https://kaiten.x5.ru/123",
            "http://localhost:8080,         1, http://localhost:8080/1"
    })
    @DisplayName("buildCardUrl нормализует trailing slash и склеивает id")
    void buildsUrlFromConfig(String base, long id, String expected) {
        assertThat(mapper.buildCardUrl(base.trim(), id)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    @DisplayName("Пустой/null/whitespace webBaseUrl → url=null")
    void emptyBaseUrlYieldsNull(String base) {
        assertThat(mapper.buildCardUrl(base, 42L)).isNull();
    }

    @Test
    @DisplayName("toDomain(dto, baseUrl) полностью маппит все поля")
    void mapsAllFieldsWithUrl() {
        KaitenCardDto dto = new KaitenCardDto(
                /* id */ 555L,
                /* title */ "Реализовать фичу",
                /* description */ "details",
                /* typeId */ 70,
                /* column */ new KaitenCardDto.KaitenColumnDto(10L, "Doing", 2),
                /* board */ new KaitenCardDto.KaitenNamedRefDto(1L, "Backend"),
                /* space */ new KaitenCardDto.KaitenNamedRefDto(2L, "Markable"),
                /* owner */ new KaitenUserDto(7L, "boris", "boris@x5.ru", "Boris", null),
                /* createdAt */ LocalDateTime.of(2026, 5, 1, 10, 0),
                /* updatedAt */ LocalDateTime.of(2026, 5, 20, 12, 0),
                /* closedAt */ null,
                /* archived */ false,
                /* members */ List.of(new KaitenMemberDto(7L, null, null, null), new KaitenMemberDto(8L, null, null, null)));

        KaitenCard card = mapper.toDomain(dto, "https://kaiten.x5.ru");

        assertAll("полный маппинг",
                () -> assertThat(card.id().value()).isEqualTo(555L),
                () -> assertThat(card.title()).isEqualTo("Реализовать фичу"),
                () -> assertThat(card.typeId()).isEqualTo(70),
                () -> assertThat(card.columnType()).isEqualTo(2),
                () -> assertThat(card.columnTitle()).isEqualTo("Doing"),
                () -> assertThat(card.boardName()).isEqualTo("Backend"),
                () -> assertThat(card.spaceName()).isEqualTo("Markable"),
                () -> assertThat(card.ownerId().value()).isEqualTo(7L),
                () -> assertThat(card.ownerName()).isEqualTo("Boris"),
                () -> assertThat(card.archived()).isFalse(),
                () -> assertThat(card.url()).isEqualTo("https://kaiten.x5.ru/555"),
                () -> assertThat(card.memberIds())
                        .extracting(m -> m.value()).containsExactly(7L, 8L));
    }

    @Test
    @DisplayName("Пустой webBaseUrl → KaitenCard.url=null, остальное полностью замаппено")
    void blankBaseUrlGivesNullCardUrl() {
        KaitenCardDto dto = new KaitenCardDto(
                42L, "t", null, 70,
                new KaitenCardDto.KaitenColumnDto(1L, "New", 1),
                new KaitenCardDto.KaitenNamedRefDto(1L, "b"),
                new KaitenCardDto.KaitenNamedRefDto(1L, "s"),
                null, null, null, null, false, List.of());

        KaitenCard card = mapper.toDomain(dto, "");

        assertAll("без url, но всё остальное на месте",
                () -> assertThat(card.url()).isNull(),
                () -> assertThat(card.id().value()).isEqualTo(42L),
                () -> assertThat(card.columnType()).isEqualTo(1),
                () -> assertThat(card.columnTitle()).isEqualTo("New"));
    }
}
