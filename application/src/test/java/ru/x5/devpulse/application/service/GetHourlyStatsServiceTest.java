package ru.x5.devpulse.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.x5.devpulse.application.port.out.CommitRepository;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.stats.HourlyBucket;
import ru.x5.devpulse.domain.model.user.Email;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetHourlyStatsService")
class GetHourlyStatsServiceTest {

    private static final Period PERIOD = new Period(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));
    private static final Email EMAIL = new Email("boris@x5.ru");

    @Mock CommitRepository commitRepository;

    @Test
    @DisplayName("Без фильтров: прокидывает Optional.empty()/empty() в репозиторий, оборачивает ячейки в HourlyStats(period)")
    void unfiltered() {
        var cells = List.of(new HourlyBucket(2, 14, 7, 320));
        when(commitRepository.aggregateHourly(PERIOD, Optional.empty(), Optional.empty())).thenReturn(cells);

        var result = new GetHourlyStatsService(commitRepository)
                .get(PERIOD, Optional.empty(), Optional.empty());

        assertAll("без фильтров",
                () -> assertThat(result.period()).isEqualTo(PERIOD),
                () -> assertThat(result.cells()).isEqualTo(cells));
    }

    @Test
    @DisplayName("Режим одного автора: прокидывает Optional.of(email) в репозиторий 1-в-1")
    void singleAuthor() {
        when(commitRepository.aggregateHourly(PERIOD, Optional.of(EMAIL), Optional.empty()))
                .thenReturn(List.of());

        var result = new GetHourlyStatsService(commitRepository)
                .get(PERIOD, Optional.of(EMAIL), Optional.empty());

        assertThat(result.cells()).isEmpty();
    }

    @Test
    @DisplayName("Режим команды: прокидывает Optional.of(team) в репозиторий 1-в-1")
    void teamFilter() {
        var cells = List.of(new HourlyBucket(0, 10, 3, 90));
        when(commitRepository.aggregateHourly(PERIOD, Optional.empty(), Optional.of("Platform")))
                .thenReturn(cells);

        var result = new GetHourlyStatsService(commitRepository)
                .get(PERIOD, Optional.empty(), Optional.of("Platform"));

        assertThat(result.cells()).isEqualTo(cells);
    }
}
