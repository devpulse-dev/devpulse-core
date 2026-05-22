package ru.x5.markable.dev.analytics.adapter.persistence.kaiten;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.x5.markable.dev.analytics.adapter.persistence.shared.PostgresContainerSupport;
import ru.x5.markable.dev.analytics.application.port.out.KaitenCardRepository;
import ru.x5.markable.dev.analytics.domain.common.Period;
import ru.x5.markable.dev.analytics.domain.model.kaiten.KaitenCard;
import ru.x5.markable.dev.analytics.domain.model.kaiten.KaitenCardId;
import ru.x5.markable.dev.analytics.domain.model.user.KaitenUserId;

@SpringBootTest
@Testcontainers
@DisplayName("KaitenCardRepositoryAdapter (карточки + участники)")
class KaitenCardRepositoryAdapterIT extends PostgresContainerSupport {

    private static final KaitenCardId CARD_ID = new KaitenCardId(900_001L);
    private static final KaitenUserId USER_1 = new KaitenUserId(1L);
    private static final KaitenUserId USER_2 = new KaitenUserId(2L);
    private static final KaitenUserId USER_3 = new KaitenUserId(3L);
    private static final LocalDateTime MAY_10_NOON = LocalDateTime.of(2026, 5, 10, 12, 0);
    private static final LocalDateTime MAY_10_AFTERNOON = LocalDateTime.of(2026, 5, 10, 13, 0);
    private static final Period MAY = new Period(
            LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));

    @Autowired
    KaitenCardRepository repo;

    @Test
    @DisplayName("upsertAll перезаписывает список участников: старые удаляются, новые сохраняются")
    void upsertReplacesMembersList() {
        // 1-й upsert: участники {USER_1, USER_2}
        repo.upsertAll(List.of(card("T1", List.of(USER_1, USER_2), MAY_10_NOON)));
        // 2-й upsert: {USER_2, USER_3} — USER_1 должен уйти
        repo.upsertAll(List.of(card("T1 v2", List.of(USER_2, USER_3), MAY_10_AFTERNOON)));

        List<KaitenCard> u1Cards = repo.findByMemberAndPeriod(USER_1, MAY);
        List<KaitenCard> u2Cards = repo.findByMemberAndPeriod(USER_2, MAY);
        List<KaitenCard> u3Cards = repo.findByMemberAndPeriod(USER_3, MAY);

        assertAll("замена списка участников карточки",
                () -> assertThat(u1Cards)
                        .as("USER_1 удалён из карточки и больше её не видит")
                        .isEmpty(),
                () -> assertThat(u2Cards)
                        .as("USER_2 остался — должна быть найдена 1 карточка")
                        .hasSize(1),
                () -> assertThat(u2Cards.getFirst().memberIds())
                        .as("в memberIds текущей версии карточки оба участника")
                        .containsExactlyInAnyOrder(USER_2, USER_3),
                () -> assertThat(u3Cards)
                        .as("USER_3 добавлен — должна быть найдена 1 карточка")
                        .hasSize(1));
    }

    private static KaitenCard card(String title, List<KaitenUserId> members,
                                   LocalDateTime updatedAt) {
        return new KaitenCard(
                CARD_ID, title, null,
                "in_progress", "Doing", "Board", "Space",
                null, null,
                updatedAt.minusDays(1), updatedAt, null, false,
                "https://kaiten.x5.ru/" + CARD_ID.value(),
                members);
    }
}
