package ru.x5.devpulse.adapter.rest;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.x5.devpulse.application.port.in.GetDailyStatsUseCase;
import ru.x5.devpulse.application.port.in.GetHourlyStatsUseCase;
import ru.x5.devpulse.application.port.in.GetPerformanceReviewUseCase;
import ru.x5.devpulse.application.port.in.GetPeriodSummaryUseCase;
import ru.x5.devpulse.application.port.in.GetReviewStatsUseCase;
import ru.x5.devpulse.application.port.in.GetWeeklyStatsUseCase;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.git.RepoName;
import ru.x5.devpulse.domain.model.performance.MetricDelta;
import ru.x5.devpulse.domain.model.performance.PerformanceHighlight;
import ru.x5.devpulse.domain.model.performance.PerformanceMetrics;
import ru.x5.devpulse.domain.model.performance.PerformanceReview;
import ru.x5.devpulse.domain.model.performance.TaskStatusCounts;
import ru.x5.devpulse.domain.model.performance.TaskTypeBreakdown;
import ru.x5.devpulse.domain.model.review.ReviewAuthorStats;
import ru.x5.devpulse.domain.model.review.ReviewStats;
import ru.x5.devpulse.domain.model.stats.AuthorSummary;
import ru.x5.devpulse.domain.model.stats.DailyAuthorStats;
import ru.x5.devpulse.domain.model.stats.HourlyBucket;
import ru.x5.devpulse.domain.model.stats.HourlyStats;
import ru.x5.devpulse.domain.model.stats.PeriodSummary;
import ru.x5.devpulse.domain.model.user.Email;
import ru.x5.devpulse.domain.model.user.KaitenUserId;
import ru.x5.devpulse.domain.model.user.UnifiedUser;

@WebMvcTest(StatsController.class)
@Import(RestMappersTestConfig.class)
@DisplayName("StatsController (/api/v2/stats)")
class StatsControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean GetDailyStatsUseCase getDailyStats;
    @MockitoBean GetWeeklyStatsUseCase getWeeklyStats;
    @MockitoBean GetPeriodSummaryUseCase getPeriodSummary;
    @MockitoBean GetHourlyStatsUseCase getHourlyStats;
    @MockitoBean GetReviewStatsUseCase getReviewStats;
    @MockitoBean GetPerformanceReviewUseCase getPerformanceReview;

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

    @Test
    @DisplayName("GET /hourly → 200, from/to + ячейки weekday×hour")
    void hourlyReturnsMatrix() throws Exception {
        when(getHourlyStats.get(any(), any())).thenReturn(new HourlyStats(
                new Period(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)),
                List.of(new HourlyBucket(2, 14, 7, 320))));

        mvc.perform(get("/api/v2/stats/hourly")
                        .param("from", "2026-05-01").param("to", "2026-05-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-05-01"))
                .andExpect(jsonPath("$.to").value("2026-05-31"))
                .andExpect(jsonPath("$.cells[0].weekday").value(2))
                .andExpect(jsonPath("$.cells[0].hour").value(14))
                .andExpect(jsonPath("$.cells[0].commits").value(7))
                .andExpect(jsonPath("$.cells[0].addedLines").value(320));
    }

    @Test
    @DisplayName("GET /reviews → 200, авторы с reviewsGiven/commentsGiven + округлённым avgTimeToMerge")
    void reviewsReturnsAuthors() throws Exception {
        when(getReviewStats.get(any())).thenReturn(new ReviewStats(
                new Period(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)),
                List.of(new ReviewAuthorStats(
                        new Email("boris@x5.ru"), "Boris", "https://kaiten.x5.ru/avatars/42.png",
                        23, 47, 14, 18.456, 11))));

        mvc.perform(get("/api/v2/stats/reviews")
                        .param("from", "2026-05-01").param("to", "2026-05-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-05-01"))
                .andExpect(jsonPath("$.authors[0].email").value("boris@x5.ru"))
                .andExpect(jsonPath("$.authors[0].reviewsGiven").value(23))
                .andExpect(jsonPath("$.authors[0].commentsGiven").value(47))
                .andExpect(jsonPath("$.authors[0].reviewsReceived").value(14))
                .andExpect(jsonPath("$.authors[0].avgTimeToMergeHours").value(18.5))
                .andExpect(jsonPath("$.authors[0].mergedMrCount").value(11));
    }

    @Test
    @DisplayName("GET /performance/review → 200: subject с team, дельты, breakdown, highlights")
    void performanceReviewReturnsDossier() throws Exception {
        var email = new Email("boris@x5.ru");
        var user = new UnifiedUser(1L, email, "boris", "Boris", null,
                new KaitenUserId(7L), null, "Platform", true,
                LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now());
        var metrics = new PerformanceMetrics(
                MetricDelta.of(100, 80.0),   // commits → delta 20, pct 25
                MetricDelta.snapshot(90), MetricDelta.snapshot(1000), MetricDelta.snapshot(500),
                MetricDelta.snapshot(200), MetricDelta.snapshot(20), MetricDelta.snapshot(40),
                MetricDelta.snapshot(10), MetricDelta.snapshot(18.5), MetricDelta.snapshot(8),
                MetricDelta.snapshot(2), MetricDelta.snapshot(5),    // defectsInWork/Closed
                MetricDelta.snapshot(3), MetricDelta.snapshot(7));   // devTasksInWork/Closed
        var breakdown = new TaskTypeBreakdown(TaskStatusCounts.of(2, 5), TaskStatusCounts.of(3, 7));
        var highlights = List.of(new PerformanceHighlight(
                PerformanceHighlight.Kind.CARD, "closed defect", "DEFECT · DONE", "https://kaiten.x5.ru/1"));
        var period = new Period(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));
        var review = new PerformanceReview(user, period, period.previousAdjacent(),
                metrics, breakdown, highlights);

        when(getPerformanceReview.review(any(), any(), anyBoolean())).thenReturn(Optional.of(review));

        mvc.perform(get("/api/v2/performance/review")
                        .param("email", "boris@x5.ru")
                        .param("from", "2026-01-01").param("to", "2026-03-31")
                        .param("compareToPrevious", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject.email").value("boris@x5.ru"))
                .andExpect(jsonPath("$.subject.team").value("Platform"))
                .andExpect(jsonPath("$.metrics.commits.current").value(100.0))
                .andExpect(jsonPath("$.metrics.commits.deltaPct").value(25.0))
                .andExpect(jsonPath("$.metrics.defectsInWork.current").value(2.0))
                .andExpect(jsonPath("$.taskBreakdown.defect.done").value(5))
                .andExpect(jsonPath("$.highlights[0].kind").value("CARD"))
                .andExpect(jsonPath("$.highlights[0].url").value("https://kaiten.x5.ru/1"));
    }

    @Test
    @DisplayName("GET /performance/review для несуществующего пользователя → 404")
    void performanceReviewNotFound() throws Exception {
        when(getPerformanceReview.review(any(), any(), anyBoolean())).thenReturn(Optional.empty());

        mvc.perform(get("/api/v2/performance/review")
                        .param("email", "unknown@x5.ru")
                        .param("from", "2026-01-01").param("to", "2026-03-31"))
                .andExpect(status().isNotFound());
    }
}
