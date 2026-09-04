package ru.x5.devpulse.domain.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.TreeMap;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.kaiten.KaitenCardId;
import ru.x5.devpulse.domain.model.kaiten.KaitenTimeLog;
import ru.x5.devpulse.domain.model.performance.TimesheetDay;
import ru.x5.devpulse.domain.model.performance.TimesheetEntry;
import ru.x5.devpulse.domain.model.review.AuthoredMergeRequest;

/**
 * Чистая логика таймшита: суммирование логов Kaiten по дню и по карточке внутри дня.
 *
 * <p>За один день бывает несколько списаний (разные карточки, несколько заходов в одну) —
 * схлопываем: день → карточки → сумма минут. Логи вне периода отбрасываем (Kaiten фильтрует
 * сам, но границы у API не всегда очевидно включительны). Stateless, без I/O.</p>
 */
public final class TimesheetAssembler {

    private TimesheetAssembler() {}

    /**
     * Индекс «карточка → её MR» по заголовкам MR: номер задачи вытаскивается тем же
     * {@link CommitMessageParser}, что и у коммитов ({@code 1700-2712833 fix…} → {@code 2712833}).
     * MR без распознанного номера в индекс не попадают.
     */
    public static Map<Long, List<AuthoredMergeRequest>> indexMergeRequestsByCard(
            Collection<AuthoredMergeRequest> mergeRequests) {
        Map<Long, List<AuthoredMergeRequest>> byCard = new LinkedHashMap<>();
        for (AuthoredMergeRequest mr : mergeRequests) {
            var task = CommitMessageParser.extractTaskNumber(mr.title());
            if (task.isEmpty()) {
                continue;
            }
            OptionalLong cardId = task.get().asKaitenCardId();
            if (cardId.isEmpty()) {
                continue;
            }
            byCard.computeIfAbsent(cardId.getAsLong(), k -> new ArrayList<>()).add(mr);
        }
        return byCard;
    }

    /**
     * Логи → дни со списаниями (по возрастанию даты), внутри дня — карточки (по убыванию минут).
     * Дни без списаний не создаются: период может быть длинным, а списаний — единицы.
     *
     * @param mergeRequestsByCard индекс из {@link #indexMergeRequestsByCard}; пустой — без MR
     */
    public static List<TimesheetDay> byDay(Collection<KaitenTimeLog> logs, Period period,
                                           Map<Long, List<AuthoredMergeRequest>> mergeRequestsByCard) {
        // date → cardId → аккумулятор. TreeMap по дате: дни сразу отсортированы.
        Map<LocalDate, Map<Long, EntryAcc>> byDate = new TreeMap<>();
        for (KaitenTimeLog log : logs) {
            LocalDate date = log.date();
            if (date == null || !period.contains(date)) {
                continue;
            }
            long cardKey = log.cardId() == null ? 0L : log.cardId().value();
            byDate.computeIfAbsent(date, d -> new LinkedHashMap<>())
                    .computeIfAbsent(cardKey, k -> new EntryAcc(log))
                    .minutes += log.minutes();
        }

        List<TimesheetDay> days = new ArrayList<>(byDate.size());
        byDate.forEach((date, byCard) -> {
            List<TimesheetEntry> entries = byCard.values().stream()
                    .map(acc -> acc.toEntry(mergeRequestsByCard))
                    .sorted(Comparator.comparingInt(TimesheetEntry::minutes).reversed())
                    .toList();
            int dayMinutes = entries.stream().mapToInt(TimesheetEntry::minutes).sum();
            days.add(new TimesheetDay(date, dayMinutes, entries));
        });
        return days;
    }

    /** Суммарные минуты по уже посчитанным дням. */
    public static int totalMinutes(Collection<TimesheetDay> days) {
        int total = 0;
        for (TimesheetDay day : days) {
            total += day.minutes();
        }
        return total;
    }

    /** Аккумулятор минут по одной карточке в пределах дня (метаданные берём из первого лога). */
    private static final class EntryAcc {
        private final KaitenTimeLog first;
        private int minutes;

        private EntryAcc(KaitenTimeLog first) {
            this.first = first;
        }

        private TimesheetEntry toEntry(Map<Long, List<AuthoredMergeRequest>> mergeRequestsByCard) {
            KaitenCardId cardId = first.cardId();
            List<AuthoredMergeRequest> mrs = cardId == null
                    ? List.of()
                    : mergeRequestsByCard.getOrDefault(cardId.value(), List.of());
            return new TimesheetEntry(
                    cardId, first.cardTitle(), first.cardUrl(), first.cardType(),
                    first.aiAgent(), minutes, mrs);
        }
    }
}
