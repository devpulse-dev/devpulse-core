package ru.x5.devpulse.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.x5.devpulse.application.port.out.KaitenGateway;
import ru.x5.devpulse.application.port.out.UnifiedUserRepository;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.kaiten.KaitenCard;
import ru.x5.devpulse.domain.model.kaiten.KaitenCardId;
import ru.x5.devpulse.domain.model.kaiten.KaitenUrgency;
import ru.x5.devpulse.domain.model.performance.DefectDetail;
import ru.x5.devpulse.domain.model.performance.TeamDefectsReport;
import ru.x5.devpulse.domain.model.user.Email;
import ru.x5.devpulse.domain.model.user.KaitenUserId;
import ru.x5.devpulse.domain.model.user.UnifiedUser;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetTeamDefectsService (дефекты команды: стрим Kaiten, дедуп, резолв участников)")
class GetTeamDefectsServiceTest {

    private static final Period Q = new Period(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30));

    @Mock private UnifiedUserRepository userRepo;
    @Mock private KaitenGateway gateway;
    private GetTeamDefectsService service;

    @BeforeEach
    void setUp() {
        service = new GetTeamDefectsService(userRepo, gateway);
    }

    @Test
    @DisplayName("Дедуп по id, только дефекты, aiAgentCount, участники резолвятся (включая не из команды)")
    void happyPath() {
        when(userRepo.findAll()).thenReturn(List.of(
                user("boris@x5.ru", 1L, "Platform"),
                user("alice@x5.ru", 2L, "Platform"),
                user("carol@x5.ru", null, "Platform"),   // без kaiten_id — не в фильтре
                user("dave@x5.ru", 9L, "OtherTeam")));    // не из команды, но резолвится как участник

        KaitenCard d100 = defect(100, KaitenUrgency.HIGH, at(2026, 4, 5), true, 1L, 2L);
        KaitenCard d101 = defect(101, KaitenUrgency.LOW, at(2026, 5, 10), false, 1L, 9L, 99L);
        KaitenCard dev102 = card(102, 70, KaitenUrgency.UNKNOWN, at(2026, 4, 10), false, 1L);
        // Две страницы; d100 приходит дважды (по двум участникам) → дедуп.
        stubStream(List.of(d100, d101, dev102), List.of(d100));

        TeamDefectsReport report = service.get("Platform", List.of(Q));

        assertAll("агрегаты",
                () -> assertThat(report.periods()).hasSize(1),
                () -> assertThat(report.periods().get(0).counts().high()).isEqualTo(1),
                () -> assertThat(report.periods().get(0).counts().low()).isEqualTo(1),
                () -> assertThat(report.periods().get(0).counts().total()).as("d100 учтён один раз").isEqualTo(2),
                () -> assertThat(report.periods().get(0).aiAgentCount()).isEqualTo(1));

        List<DefectDetail> defects = report.defects();
        assertAll("детализация",
                () -> assertThat(defects).extracting(d -> d.id().value()).containsExactly(101L, 100L), // desc createdAt
                () -> assertThat(defects.get(0).members()).extracting(m -> m.email().value())
                        .containsExactlyInAnyOrder("boris@x5.ru", "dave@x5.ru"), // 99 не резолвится → отброшен
                () -> assertThat(defects.get(1).members()).extracting(m -> m.email().value())
                        .containsExactlyInAnyOrder("boris@x5.ru", "alice@x5.ru"),
                () -> assertThat(defects.get(1).aiAgent()).isTrue());
    }

    @Test
    @DisplayName("Нет участников с kaiten_id → пустой отчёт, Kaiten не дёргаем")
    void noKaitenMembers() {
        when(userRepo.findAll()).thenReturn(List.of(user("carol@x5.ru", null, "Platform")));

        TeamDefectsReport report = service.get("Platform", List.of(Q));

        assertAll(
                () -> assertThat(report.periods().get(0).counts().total()).isZero(),
                () -> assertThat(report.defects()).isEmpty());
        verify(gateway, never()).streamCards(anyList(), any(), any());
    }

    @Test
    @DisplayName("Пустой список периодов → пустой отчёт, без обращений к репозиторию/Kaiten")
    void emptyPeriods() {
        TeamDefectsReport report = service.get("Platform", List.of());

        assertThat(report.periods()).isEmpty();
        assertThat(report.defects()).isEmpty();
        verifyNoInteractions(userRepo, gateway);
    }

    @SuppressWarnings("unchecked")
    private void stubStream(List<KaitenCard> page1, List<KaitenCard> page2) {
        doAnswer(inv -> {
            Consumer<List<KaitenCard>> handler = inv.getArgument(2);
            handler.accept(page1);
            handler.accept(page2);
            return null;
        }).when(gateway).streamCards(anyList(), any(), any(Consumer.class));
    }

    private static LocalDateTime at(int y, int m, int d) {
        return LocalDateTime.of(y, m, d, 10, 0);
    }

    private static KaitenCard defect(long id, KaitenUrgency urgency, LocalDateTime created,
                                     boolean aiAgent, long... members) {
        return card(id, 8, urgency, created, aiAgent, members);
    }

    private static KaitenCard card(long id, int typeId, KaitenUrgency urgency, LocalDateTime created,
                                   boolean aiAgent, long... members) {
        List<KaitenUserId> memberIds = Arrays.stream(members).mapToObj(KaitenUserId::new).toList();
        return new KaitenCard(
                new KaitenCardId(id), "card" + id, null, typeId, 2,
                "col", "board", "space", null, null,
                created, created, null, false, "https://k/" + id, memberIds,
                urgency, null, null, null, null, null, aiAgent);
    }

    private static UnifiedUser user(String email, Long kaitenId, String team) {
        return new UnifiedUser(
                1L, new Email(email), email, email, null,
                kaitenId == null ? null : new KaitenUserId(kaitenId), null, team, false,
                LocalDateTime.now(), LocalDateTime.now(), null);
    }
}
