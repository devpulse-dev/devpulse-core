package ru.x5.devpulse.application.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.x5.devpulse.application.port.in.GetTimesheetUseCase;
import ru.x5.devpulse.application.port.out.KaitenGateway;
import ru.x5.devpulse.application.port.out.ReviewStatsRepository;
import ru.x5.devpulse.application.port.out.UnifiedUserRepository;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.kaiten.KaitenTimeLog;
import ru.x5.devpulse.domain.model.performance.Timesheet;
import ru.x5.devpulse.domain.model.performance.TimesheetDay;
import ru.x5.devpulse.domain.model.review.AuthoredMergeRequest;
import ru.x5.devpulse.domain.model.user.Email;
import ru.x5.devpulse.domain.model.user.KaitenUserId;
import ru.x5.devpulse.domain.service.TimesheetAssembler;

/**
 * Таймшит разработчика: email → {@code kaiten_id} → live-выборка {@code /time-logs} за период →
 * суммирование по дням в чистом {@link TimesheetAssembler}.
 *
 * <p>Нет пользователя или у него нет {@code kaiten_id} → пустой таймшит (не 404): для UI это
 * валидное состояние «списаний нет», а Kaiten без id дёргать бессмысленно.</p>
 *
 * <p>MR связываются с карточками эвристикой: номер задачи парсится из заголовка MR тем же
 * {@code CommitMessageParser}, что и у коммитов. MR без номера в заголовке в детализацию
 * не попадут — это осознанный компромисс (прямой связи MR↔карточка в данных нет).</p>
 */
@Slf4j
@RequiredArgsConstructor
public final class GetTimesheetService implements GetTimesheetUseCase {

    private final UnifiedUserRepository unifiedUserRepository;
    private final KaitenGateway kaitenGateway;
    private final ReviewStatsRepository reviewStatsRepository;

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

        // MR автора (по индексу author_email) → индекс «карточка → MR» по номеру задачи
        // в заголовке MR. Без периода: MR мог быть открыт задолго до списания времени.
        List<AuthoredMergeRequest> authorMrs = reviewStatsRepository.findMergeRequestsByAuthor(email);
        Map<Long, List<AuthoredMergeRequest>> mrsByCard =
                TimesheetAssembler.indexMergeRequestsByCard(authorMrs);

        List<TimesheetDay> days = TimesheetAssembler.byDay(logs, period, mrsByCard);
        int totalMinutes = TimesheetAssembler.totalMinutes(days);

        log.info("timesheet {}: {} логов → {} дней, {} минут; MR автора {} (связаны с {} карточками)",
                email.value(), logs.size(), days.size(), totalMinutes,
                authorMrs.size(), mrsByCard.size());
        return new Timesheet(email, period, totalMinutes, days);
    }
}
