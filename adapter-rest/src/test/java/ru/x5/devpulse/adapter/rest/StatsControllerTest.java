package ru.x5.devpulse.adapter.rest;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.x5.devpulse.application.port.in.GetDailyStatsUseCase;
import ru.x5.devpulse.application.port.in.GetPeriodSummaryUseCase;
import ru.x5.devpulse.application.port.in.GetWeeklyStatsUseCase;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.git.RepoName;
import ru.x5.devpulse.domain.model.stats.AuthorSummary;
import ru.x5.devpulse.domain.model.stats.DailyAuthorStats;
import ru.x5.devpulse.domain.model.stats.PeriodSummary;
import ru.x5.devpulse.domain.model.user.Email;

@WebMvcTest(StatsController.class)
@Import(RestMappersTestConfig.class)
@DisplayName("StatsController (/api/v2/stats)")
class StatsControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean GetDailyStatsUseCase getDailyStats;
    @MockitoBean GetWeeklyStatsUseCase getWeeklyStats;
    @MockitoBean GetPeriodSummaryUseCase getPeriodSummary;

    @Test
    @DisplayName("GET /daily?from=&to= возвращает 200 и список агрегатов с email/repo")
    void dailyReturnsList() throws Exception {
        when(getDailyStats.findByPeriod(any())).thenReturn(List.of(new DailyAuthorStats(
                1L, new Email("a@x5.ru"), LocalDate.of(2026, 5, 10),
                new RepoName("xrg-core"), 3, 0, 10, 5, 1,
                LocalDateTime.now(), 42L)));

        mvc.perform(get("/api/v2/stats/daily")
                        .param("from", "2026-05-01").param("to", "2026-05-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("a@x5.ru"))
                .andExpect(jsonPath("$[0].repo").value("xrg-core"))
                .andExpect(jsonPath("$[0].commits").value(3));
    }

    @Test
    @DisplayName("GET /summary с пустыми данными → 200 с нулевой сводкой")
    void summaryReturnsEmpty() throws Exception {
        when(getPeriodSummary.summarize(any())).thenReturn(new PeriodSummary(
                new Period(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)),
                0, 0, 0, 0, 0, 0, List.<AuthorSummary>of()));

        mvc.perform(get("/api/v2/stats/summary")
                        .param("from", "2026-05-01").param("to", "2026-05-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCommits").value(0))
                .andExpect(jsonPath("$.uniqueAuthors").value(0));
    }

    @Test
    @DisplayName("GET /daily без обязательного from → 400 RFC 7807 problem+json")
    void missingFromReturns400ProblemDetail() throws Exception {
        mvc.perform(get("/api/v2/stats/daily").param("to", "2026-05-31"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value(containsString("Malformed")));
    }
}
