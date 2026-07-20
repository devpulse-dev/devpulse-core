package ru.x5.devpulse.adapter.rest;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import ru.x5.devpulse.application.port.in.GetTeamDefectsUseCase;
import ru.x5.devpulse.application.port.in.GetWeeklyStatsUseCase;
import ru.x5.devpulse.application.port.in.GetMergedMrStatsUseCase;
import ru.x5.devpulse.application.port.in.MarkDefectsAiAgentUseCase;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.git.RepoName;
import ru.x5.devpulse.domain.model.kaiten.KaitenCardType;
import ru.x5.devpulse.domain.model.kaiten.KaitenColumnStatus;
import ru.x5.devpulse.domain.model.kaiten.KaitenUrgency;
import ru.x5.devpulse.domain.model.performance.AiAgentMarkResult;
import ru.x5.devpulse.domain.model.performance.CycleTime;
import ru.x5.devpulse.domain.model.performance.CycleTimeBreakdown;
import ru.x5.devpulse.domain.model.performance.DefectDetail;
import ru.x5.devpulse.domain.model.performance.DefectMember;
import ru.x5.devpulse.domain.model.performance.DefectsSummary;
import ru.x5.devpulse.domain.model.performance.PeriodDefectCounts;
import ru.x5.devpulse.domain.model.performance.TeamDefectsReport;
import ru.x5.devpulse.domain.model.performance.DeliveredFeature;
import ru.x5.devpulse.domain.model.performance.DevelopmentRollup;
import ru.x5.devpulse.domain.model.performance.FirefightingItem;
import ru.x5.devpulse.domain.model.performance.KaitenInsights;
import ru.x5.devpulse.domain.model.performance.MetricDelta;
import ru.x5.devpulse.domain.model.performance.NotableResults;
import ru.x5.devpulse.domain.model.performance.PerformanceMetrics;
import ru.x5.devpulse.domain.model.performance.PerformanceReview;
import ru.x5.devpulse.domain.model.performance.RootTask;
import ru.x5.devpulse.domain.model.performance.TaskStatusCounts;
import ru.x5.devpulse.domain.model.performance.TaskTypeBreakdown;
import ru.x5.devpulse.domain.model.performance.UrgencyCounts;
import ru.x5.devpulse.domain.model.performance.UseCaseRef;
import ru.x5.devpulse.domain.model.performance.WorkBalance;
import ru.x5.devpulse.domain.model.review.AuthorMergedMrCount;
import ru.x5.devpulse.domain.model.review.RepoMergedMrCount;
import ru.x5.devpulse.domain.model.review.ReviewAuthorStats;
import ru.x5.devpulse.domain.model.review.ReviewStats;
import ru.x5.devpulse.domain.model.review.TeamMergedMrStats;
import ru.x5.devpulse.domain.model.stats.AuthorSummary;
import ru.x5.devpulse.domain.model.stats.DailyAuthorStats;
import ru.x5.devpulse.domain.model.stats.HourlyBucket;
import ru.x5.devpulse.domain.model.stats.HourlyStats;
import ru.x5.devpulse.domain.model.kaiten.KaitenCardId;
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
    @MockitoBean GetTeamDefectsUseCase getTeamDefects;
    @MockitoBean GetMergedMrStatsUseCase getMergedMrStats;
    @MockitoBean MarkDefectsAiAgentUseCase markDefectsAiAgent;

    @Test
    @DisplayName("GET /daily?from=&to= возвращает 200 и список агрегатов с email/repo")
    void dailyReturnsList() throws Exception {
        when(getDailyStats.findByPeriod(any(), any(), any())).thenReturn(List.of(new DailyAuthorStats(
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
    @DisplayName("GET /daily?email=&team= прокидывает опциональные фильтры в use case")
    void dailyPassesOptionalFilters() throws Exception {
        when(getDailyStats.findByPeriod(any(), any(), any())).thenReturn(List.of());

        mvc.perform(get("/api/v2/stats/daily")
                        .param("from", "2026-05-01").param("to", "2026-05-31")
                        .param("team", "Platform"))
                .andExpect(status().isOk());

        // email отсутствует → Optional.empty(); team задан → Optional.of("Platform")
        verify(getDailyStats).findByPeriod(any(), eq(Optional.empty()), eq(Optional.of("Platform")));
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
        when(getHourlyStats.get(any(), any(), any())).thenReturn(new HourlyStats(
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
    @DisplayName("GET /hourly?team=Platform → фильтр команды прокинут в use case (email пуст)")
    void hourlyForwardsTeamFilter() throws Exception {
        when(getHourlyStats.get(any(), any(), any())).thenReturn(new HourlyStats(
                new Period(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)), List.of()));

        mvc.perform(get("/api/v2/stats/hourly")
                        .param("from", "2026-05-01").param("to", "2026-05-31")
                        .param("team", "Platform"))
                .andExpect(status().isOk());

        verify(getHourlyStats).get(any(), eq(Optional.empty()), eq(Optional.of("Platform")));
    }

    @Test
    @DisplayName("GET /reviews → 200, авторы с reviewsGiven/commentsGiven + округлённым avgTimeToMerge")
    void reviewsReturnsAuthors() throws Exception {
        when(getReviewStats.get(any())).thenReturn(new ReviewStats(
                new Period(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)),
                List.of(new ReviewAuthorStats(
                        new Email("boris@x5.ru"), "Boris", "https://kaiten.x5.ru/avatars/42.png",
                        23, 47, 14, 18.456, 11, "Маркировка", true))));

        mvc.perform(get("/api/v2/stats/reviews")
                        .param("from", "2026-05-01").param("to", "2026-05-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-05-01"))
                .andExpect(jsonPath("$.authors[0].email").value("boris@x5.ru"))
                .andExpect(jsonPath("$.authors[0].reviewsGiven").value(23))
                .andExpect(jsonPath("$.authors[0].commentsGiven").value(47))
                .andExpect(jsonPath("$.authors[0].reviewsReceived").value(14))
                .andExpect(jsonPath("$.authors[0].avgTimeToMergeHours").value(18.5))
                .andExpect(jsonPath("$.authors[0].mergedMrCount").value(11))
                .andExpect(jsonPath("$.authors[0].team").value("Маркировка"))
                .andExpect(jsonPath("$.authors[0].isLead").value(true));
    }

    @Test
    @DisplayName("GET /performance/review → 200: subject, дельты, kaiten-блок, notable")
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
        var notable = new NotableResults(
                List.of(new FirefightingItem(1L, "closed critical defect",
                        "https://kaiten.x5.ru/1", KaitenUrgency.CRITICAL)),
                List.of(new DeliveredFeature(100L, "Feature A", "https://kaiten.x5.ru/100", 5, 6)));
        var kaiten = new KaitenInsights(
                new DefectsSummary(15, 4, 11, 6, new UrgencyCounts(4, 2, 5, 4, 0)),
                new DevelopmentRollup(3, 1, List.of(new RootTask(
                        100L, "Root A", "https://kaiten.x5.ru/100",
                        List.of(new UseCaseRef(11L, "UC1", "https://kaiten.x5.ru/11",
                                KaitenColumnStatus.DONE, KaitenCardType.DEVELOPMENT))))),
                new CycleTimeBreakdown(new CycleTime(2.0, 2.5, 4), new CycleTime(7.0, 8.2, 8)),
                WorkBalance.of(15, 3));
        var period = new Period(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));
        var review = new PerformanceReview(user, period, period.previousAdjacent(),
                metrics, breakdown, kaiten, notable);

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
                // notable: тушение пожаров + доставленные доработки
                .andExpect(jsonPath("$.notable.firefighting[0].title").value("closed critical defect"))
                .andExpect(jsonPath("$.notable.firefighting[0].urgency").value("CRITICAL"))
                .andExpect(jsonPath("$.notable.deliveredFeatures[0].title").value("Feature A"))
                .andExpect(jsonPath("$.notable.deliveredFeatures[0].doneCount").value(5))
                // kaiten-блок: дефекты по срочности, rollup разработки, cycle-time, баланс
                .andExpect(jsonPath("$.kaiten.defects.total").value(15))
                .andExpect(jsonPath("$.kaiten.defects.criticalHigh").value(6))
                .andExpect(jsonPath("$.kaiten.defects.byUrgency.critical").value(4))
                .andExpect(jsonPath("$.kaiten.development.useCaseCount").value(3))
                .andExpect(jsonPath("$.kaiten.development.rootTaskCount").value(1))
                .andExpect(jsonPath("$.kaiten.development.roots[0].title").value("Root A"))
                .andExpect(jsonPath("$.kaiten.development.roots[0].useCases[0].type").value("DEVELOPMENT"))
                .andExpect(jsonPath("$.kaiten.cycleTime.defects.medianDays").value(2.0))
                .andExpect(jsonPath("$.kaiten.cycleTime.development.medianDays").value(7.0))
                .andExpect(jsonPath("$.kaiten.balance.defectCount").value(15))
                .andExpect(jsonPath("$.kaiten.balance.buildCount").value(3));
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

    @Test
    @DisplayName("POST /defects → 200: периоды (byPriority/total/aiAgentCount) + детализация с участниками")
    void defectsReturnsPeriodsAndDetails() throws Exception {
        Period period = new Period(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));
        TeamDefectsReport report = new TeamDefectsReport(
                "Platform",
                List.of(new PeriodDefectCounts(period, new UrgencyCounts(2, 5, 4, 3, 0), 6)),
                List.of(new DefectDetail(
                        new KaitenCardId(150766L), "Не грузится отчёт", "https://kaiten.x5.ru/150766",
                        LocalDateTime.of(2026, 4, 5, 10, 0), true,
                        List.of(new DefectMember(new Email("boris@x5.ru"), "Boris", "http://a")))));
        when(getTeamDefects.get(eq("Platform"), any())).thenReturn(report);

        mvc.perform(post("/api/v2/stats/defects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"team\":\"Platform\",\"periods\":[{\"from\":\"2026-04-01\",\"to\":\"2026-04-30\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.team").value("Platform"))
                .andExpect(jsonPath("$.periods[0].total").value(14))
                .andExpect(jsonPath("$.periods[0].aiAgentCount").value(6))
                .andExpect(jsonPath("$.periods[0].byPriority.high").value(5))
                .andExpect(jsonPath("$.defects[0].id").value(150766))
                .andExpect(jsonPath("$.defects[0].aiAgent").value(true))
                .andExpect(jsonPath("$.defects[0].members[0].email").value("boris@x5.ru"));
    }

    @Test
    @DisplayName("POST /defects с пустой командой → 400 (раздел team-scoped)")
    void defectsBlankTeamReturns400() throws Exception {
        mvc.perform(post("/api/v2/stats/defects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"team\":\"  \",\"periods\":[{\"from\":\"2026-04-01\",\"to\":\"2026-04-30\"}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /merged-mrs → 200: total + разбивки по авторам и репозиториям")
    void mergedMrsReturnsStats() throws Exception {
        Period period = new Period(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));
        TeamMergedMrStats stats = new TeamMergedMrStats(
                "Platform", period, 37,
                List.of(new AuthorMergedMrCount(new Email("boris@x5.ru"), "Boris", "http://a", 21)),
                List.of(new RepoMergedMrCount("gkr/xrg-markable", 23)));
        when(getMergedMrStats.get(eq("Platform"), any())).thenReturn(stats);

        mvc.perform(get("/api/v2/stats/merged-mrs")
                        .param("from", "2026-05-01").param("to", "2026-05-31").param("team", "Platform"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(37))
                .andExpect(jsonPath("$.authors[0].email").value("boris@x5.ru"))
                .andExpect(jsonPath("$.authors[0].count").value(21))
                .andExpect(jsonPath("$.byRepo[0].repo").value("gkr/xrg-markable"))
                .andExpect(jsonPath("$.byRepo[0].count").value(23));
    }

    @Test
    @DisplayName("POST /defects/ai-agent → 200: requested/updated/failedIds; cardIds → use-case")
    void markAiAgentReturnsResult() throws Exception {
        when(markDefectsAiAgent.mark(any()))
                .thenReturn(new AiAgentMarkResult(2, 1, List.of(new KaitenCardId(273814L))));

        mvc.perform(post("/api/v2/stats/defects/ai-agent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cardIds\":[150766,273814]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requested").value(2))
                .andExpect(jsonPath("$.updated").value(1))
                .andExpect(jsonPath("$.failedIds[0]").value(273814));
        verify(markDefectsAiAgent).mark(List.of(new KaitenCardId(150766L), new KaitenCardId(273814L)));
    }
}
