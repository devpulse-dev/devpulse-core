package ru.x5.devpulse.application.service;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.x5.devpulse.application.port.in.GetTimesheetUseCase;
import ru.x5.devpulse.application.port.out.KaitenGateway;
import ru.x5.devpulse.application.port.out.UnifiedUserRepository;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.kaiten.KaitenTimeLog;
import ru.x5.devpulse.domain.model.performance.Timesheet;
import ru.x5.devpulse.domain.model.performance.TimesheetDay;
import ru.x5.devpulse.domain.model.user.Email;
import ru.x5.devpulse.domain.model.user.KaitenUserId;
import ru.x5.devpulse.domain.service.TimesheetAssembler;

/**
 * Таймшит разработчика: email → {@code kaiten_id} → live-выборка {@code /time-logs} за период →
 * суммирование по дням в чистом {@link TimesheetAssembler}.
 *
 * <p>Нет пользователя или у него нет {@code kaiten_id} → пустой таймшит (не 404): для UI это
 * валидное состояние «списаний нет», а Kaiten без id дёргать бессмысленно.</p>
 */
@Slf4j
@RequiredArgsConstructor
public final class GetTimesheetService implements GetTimesheetUseCase {

    private final UnifiedUserRepository unifiedUserRepository;
    private final KaitenGateway kaitenGateway;

    @Override
    public Timesheet get(Email email, Period period) {
        Optional<KaitenUserId> kaitenId = unifiedUserRepository.findByEmail(email)
                .flatMap(user -> user.kaiten());
        if (kaitenId.isEmpty()) {
            log.info("timesheet {}: нет kaiten_id — пустой таймшит", email.value());
            return new Timesheet(email, period, 0, List.of());
        }

        List<KaitenTimeLog> logs =
                kaitenGateway.fetchTimeLogs(kaitenId.get(), period.from(), period.to());
        List<TimesheetDay> days = TimesheetAssembler.byDay(logs, period);
        int totalMinutes = TimesheetAssembler.totalMinutes(days);

        log.info("timesheet {}: {} логов → {} дней, {} минут",
                email.value(), logs.size(), days.size(), totalMinutes);
        return new Timesheet(email, period, totalMinutes, days);
    }
}
