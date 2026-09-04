package ru.x5.devpulse.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.kaiten.KaitenCardId;
import ru.x5.devpulse.domain.model.kaiten.KaitenCardType;
import ru.x5.devpulse.domain.model.kaiten.KaitenTimeLog;
import ru.x5.devpulse.domain.model.performance.TimesheetDay;
import ru.x5.devpulse.domain.model.review.AuthoredMergeRequest;

@DisplayName("TimesheetAssembler (списания Kaiten → дни таймшита)")
class TimesheetAssemblerTest {

    private static final Period MAY = new Period(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));

    @Test
    @DisplayName("Несколько логов за один день схлопываются в сумму, дни по возрастанию даты")
    void sumsPerDaySorted() {
        List<KaitenTimeLog> logs = List.of(
                log(LocalDate.of(2026, 5, 5), 120, 10L),
                log(LocalDate.of(2026, 5, 4), 480, 10L),
                log(LocalDate.of(2026, 5, 5), 60, 10L),
                log(LocalDate.of(2026, 5, 5), 30, 11L));

        List<TimesheetDay> days = TimesheetAssembler.byDay(logs, MAY, Map.of());

        assertAll(
                () -> assertThat(days).extracting(TimesheetDay::date)
                        .containsExactly(LocalDate.of(2026, 5, 4), LocalDate.of(2026, 5, 5)),
                () -> assertThat(days.get(0).minutes()).isEqualTo(480),
                () -> assertThat(days.get(1).minutes()).as("120+60+30").isEqualTo(210),
                () -> assertThat(days.get(1).entries()).as("две карточки в дне").hasSize(2),
                () -> assertThat(days.get(1).entries().get(0).minutes()).as("по убыванию минут").isEqualTo(180),
                () -> assertThat(TimesheetAssembler.totalMinutes(days)).isEqualTo(690));
    }

    @Test
    @DisplayName("Логи вне периода и без даты отбрасываются; границы включительны")
    void filtersOutOfPeriodAndNullDate() {
        List<KaitenTimeLog> logs = List.of(
                log(LocalDate.of(2026, 5, 1), 60, 10L),    // граница from
                log(LocalDate.of(2026, 5, 31), 60, 10L),   // граница to
                log(LocalDate.of(2026, 4, 30), 999, 10L),  // до периода
                log(LocalDate.of(2026, 6, 1), 999, 10L),   // после периода
                log(null, 999, 10L));                      // без даты

        List<TimesheetDay> days = TimesheetAssembler.byDay(logs, MAY, Map.of());

        assertAll(
                () -> assertThat(days).hasSize(2),
                () -> assertThat(TimesheetAssembler.totalMinutes(days)).isEqualTo(120));
    }

    @Test
    @DisplayName("Пустой вход → пустой список дней и нулевой итог (дни без списаний не создаются)")
    void emptyInput() {
        List<TimesheetDay> days = TimesheetAssembler.byDay(List.of(), MAY, Map.of());

        assertThat(days).isEmpty();
        assertThat(TimesheetAssembler.totalMinutes(days)).isZero();
    }

    @Test
    @DisplayName("Внутри дня время схлопывается по карточке; метаданные (тип, AI, ссылка) сохраняются")
    void groupsEntriesByCard() {
        List<KaitenTimeLog> logs = List.of(
                new KaitenTimeLog(LocalDate.of(2026, 5, 4), 120, new KaitenCardId(2712833L),
                        "Дефект A", "https://kaiten.x5.ru/2712833", KaitenCardType.DEFECT, true),
                new KaitenTimeLog(LocalDate.of(2026, 5, 4), 60, new KaitenCardId(2712833L),
                        "Дефект A", "https://kaiten.x5.ru/2712833", KaitenCardType.DEFECT, true),
                new KaitenTimeLog(LocalDate.of(2026, 5, 4), 300, new KaitenCardId(2716972L),
                        "Разработка 69", "https://kaiten.x5.ru/2716972", KaitenCardType.DEVELOPMENT, false));

        var entries = TimesheetAssembler.byDay(logs, MAY, Map.of()).get(0).entries();

        assertAll(
                () -> assertThat(entries).hasSize(2),
                () -> assertThat(entries.get(0).cardId().value()).as("больше минут — выше").isEqualTo(2716972L),
                () -> assertThat(entries.get(0).type()).isEqualTo(KaitenCardType.DEVELOPMENT),
                () -> assertThat(entries.get(1).minutes()).as("120+60").isEqualTo(180),
                () -> assertThat(entries.get(1).aiAgent()).isTrue(),
                () -> assertThat(entries.get(1).url()).isEqualTo("https://kaiten.x5.ru/2712833"));
    }

    @Test
    @DisplayName("indexMergeRequestsByCard: номер задачи из заголовка MR; без номера — не индексируется")
    void indexesMergeRequestsByCard() {
        var index = TimesheetAssembler.indexMergeRequestsByCard(List.of(
                new AuthoredMergeRequest("gkr/core", "1700-2712833 fix", "https://scm/mr/1"),
                new AuthoredMergeRequest("gkr/mark", "1700-2712833 ui", "https://scm/mr/2"),
                new AuthoredMergeRequest("gkr/core", "no task number here", "https://scm/mr/3")));

        assertAll(
                () -> assertThat(index).containsOnlyKeys(2712833L),
                () -> assertThat(index.get(2712833L)).hasSize(2),
                () -> assertThat(index.get(2712833L)).extracting(AuthoredMergeRequest::repo)
                        .containsExactly("gkr/core", "gkr/mark"));
    }

    @Test
    @DisplayName("MR подставляются в entry своей карточки")
    void attachesMergeRequestsToEntry() {
        var mrs = Map.of(2712833L, List.of(
                new AuthoredMergeRequest("gkr/core", "1700-2712833 fix", "https://scm/mr/1")));
        List<KaitenTimeLog> logs = List.of(
                new KaitenTimeLog(LocalDate.of(2026, 5, 4), 60, new KaitenCardId(2712833L),
                        "Дефект A", "https://k/1", KaitenCardType.DEFECT, false),
                new KaitenTimeLog(LocalDate.of(2026, 5, 4), 60, new KaitenCardId(999L),
                        "Другая", "https://k/2", KaitenCardType.TASK, false));

        var entries = TimesheetAssembler.byDay(logs, MAY, mrs).get(0).entries();

        assertAll(
                () -> assertThat(entries).filteredOn(e -> e.cardId().value() == 2712833L)
                        .singleElement()
                        .satisfies(e -> assertThat(e.mergeRequests()).hasSize(1)),
                () -> assertThat(entries).filteredOn(e -> e.cardId().value() == 999L)
                        .singleElement()
                        .satisfies(e -> assertThat(e.mergeRequests()).isEmpty()));
    }

    private static KaitenTimeLog log(LocalDate date, int minutes, long cardId) {
        return new KaitenTimeLog(date, minutes, new KaitenCardId(cardId), "card" + cardId,
                "https://kaiten.x5.ru/" + cardId, KaitenCardType.DEFECT, false);
    }
}
