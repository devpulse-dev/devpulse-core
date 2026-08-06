package ru.x5.devpulse.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.x5.devpulse.application.port.out.KaitenGateway;
import ru.x5.devpulse.application.port.out.UnifiedUserRepository;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.kaiten.KaitenTimeLog;
import ru.x5.devpulse.domain.model.performance.Timesheet;
import ru.x5.devpulse.domain.model.user.Email;
import ru.x5.devpulse.domain.model.user.KaitenUserId;
import ru.x5.devpulse.domain.model.user.UnifiedUser;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetTimesheetService (таймшит разработчика из Kaiten time-logs)")
class GetTimesheetServiceTest {

    private static final Email BORIS = new Email("boris@x5.ru");
    private static final Period MAY = new Period(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));

    @Mock private UnifiedUserRepository userRepo;
    @Mock private KaitenGateway gateway;
    private GetTimesheetService service;

    @BeforeEach
    void setUp() {
        service = new GetTimesheetService(userRepo, gateway);
    }

    @Test
    @DisplayName("Логи суммируются по дням; период проброшен в Kaiten по kaiten_id")
    void aggregatesByDay() {
        when(userRepo.findByEmail(BORIS)).thenReturn(Optional.of(user(BORIS, 1579L)));
        when(gateway.fetchTimeLogs(new KaitenUserId(1579L), MAY.from(), MAY.to())).thenReturn(List.of(
                new KaitenTimeLog(LocalDate.of(2026, 5, 4), 480),
                new KaitenTimeLog(LocalDate.of(2026, 5, 5), 300),
                new KaitenTimeLog(LocalDate.of(2026, 5, 5), 210)));

        Timesheet ts = service.get(BORIS, MAY);

        assertAll(
                () -> assertThat(ts.email()).isEqualTo(BORIS),
                () -> assertThat(ts.totalMinutes()).isEqualTo(990),
                () -> assertThat(ts.loggedDays()).isEqualTo(2),
                () -> assertThat(ts.days().get(1).minutes()).as("300+210").isEqualTo(510));
        verify(gateway).fetchTimeLogs(new KaitenUserId(1579L), MAY.from(), MAY.to());
    }

    @Test
    @DisplayName("Нет kaiten_id → пустой таймшит, Kaiten не дёргаем")
    void noKaitenId() {
        when(userRepo.findByEmail(BORIS)).thenReturn(Optional.of(user(BORIS, null)));

        Timesheet ts = service.get(BORIS, MAY);

        assertAll(
                () -> assertThat(ts.totalMinutes()).isZero(),
                () -> assertThat(ts.days()).isEmpty(),
                () -> assertThat(ts.loggedDays()).isZero());
        verifyNoInteractions(gateway);
    }

    @Test
    @DisplayName("Пользователя нет в unified_user → пустой таймшит (не ошибка)")
    void unknownUser() {
        when(userRepo.findByEmail(any())).thenReturn(Optional.empty());

        Timesheet ts = service.get(BORIS, MAY);

        assertThat(ts.totalMinutes()).isZero();
        assertThat(ts.days()).isEmpty();
        verifyNoInteractions(gateway);
    }

    private static UnifiedUser user(Email email, Long kaitenId) {
        return new UnifiedUser(
                1L, email, "boris", "Boris", null,
                kaitenId == null ? null : new KaitenUserId(kaitenId), null, "Platform", false,
                LocalDateTime.now(), LocalDateTime.now(), null);
    }
}
