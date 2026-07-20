package ru.x5.devpulse.adapter.rest;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.x5.devpulse.adapter.rest.api.StatsApi;
import ru.x5.devpulse.adapter.rest.api.model.DailyStats;
import ru.x5.devpulse.adapter.rest.api.model.DefectsByPeriodRequest;
import ru.x5.devpulse.adapter.rest.api.model.DefectsByPeriodResponse;
import ru.x5.devpulse.adapter.rest.api.model.HourlyStats;
import ru.x5.devpulse.adapter.rest.api.model.MarkDefectsAiAgentRequest;
import ru.x5.devpulse.adapter.rest.api.model.MarkDefectsAiAgentResponse;
import ru.x5.devpulse.adapter.rest.api.model.MergedMrStats;
import ru.x5.devpulse.adapter.rest.api.model.PerformanceReview;
import ru.x5.devpulse.adapter.rest.api.model.PeriodSummary;
import ru.x5.devpulse.adapter.rest.api.model.ReviewStats;
import ru.x5.devpulse.adapter.rest.api.model.WeeklyStats;
import ru.x5.devpulse.adapter.rest.mapper.DailyStatsMapper;
import ru.x5.devpulse.adapter.rest.mapper.DefectsByPeriodMapper;
import ru.x5.devpulse.adapter.rest.mapper.HourlyStatsMapper;
import ru.x5.devpulse.adapter.rest.mapper.MergedMrStatsMapper;
import ru.x5.devpulse.adapter.rest.mapper.PerformanceReviewMapper;
import ru.x5.devpulse.adapter.rest.mapper.PeriodSummaryMapper;
import ru.x5.devpulse.adapter.rest.mapper.ReviewStatsMapper;
import ru.x5.devpulse.adapter.rest.mapper.WeeklyStatsMapper;
import ru.x5.devpulse.application.port.in.GetDailyStatsUseCase;
import ru.x5.devpulse.application.port.in.GetHourlyStatsUseCase;
import ru.x5.devpulse.application.port.in.GetMergedMrStatsUseCase;
import ru.x5.devpulse.application.port.in.GetPerformanceReviewUseCase;
import ru.x5.devpulse.application.port.in.GetPeriodSummaryUseCase;
import ru.x5.devpulse.application.port.in.GetReviewStatsUseCase;
import ru.x5.devpulse.application.port.in.GetTeamDefectsUseCase;
import ru.x5.devpulse.application.port.in.GetWeeklyStatsUseCase;
import ru.x5.devpulse.application.port.in.MarkDefectsAiAgentUseCase;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.kaiten.KaitenCardId;
import ru.x5.devpulse.domain.model.performance.AiAgentMarkResult;
import ru.x5.devpulse.domain.model.user.Email;

/**
 * Query-эндпоинты статистики: дневная, недельная, сводка за период, почасовая.
 * Контракт — {@link StatsApi}, сгенерированный из {@code stats-api.yaml}.
 */
@RestController
@RequiredArgsConstructor
class StatsController implements StatsApi {

    private final GetDailyStatsUseCase getDailyStats;
    private final GetWeeklyStatsUseCase getWeeklyStats;
    private final GetPeriodSummaryUseCase getPeriodSummary;
    private final GetHourlyStatsUseCase getHourlyStats;
    private final GetReviewStatsUseCase getReviewStats;
    private final GetPerformanceReviewUseCase getPerformanceReview;
    private final GetTeamDefectsUseCase getTeamDefects;
    private final GetMergedMrStatsUseCase getMergedMrStats;
    private final MarkDefectsAiAgentUseCase markDefectsAiAgent;

    private final DailyStatsMapper dailyStatsMapper;
    private final WeeklyStatsMapper weeklyStatsMapper;
    private final PeriodSummaryMapper periodSummaryMapper;
    private final HourlyStatsMapper hourlyStatsMapper;
    private final ReviewStatsMapper reviewStatsMapper;
    private final PerformanceReviewMapper performanceReviewMapper;
    private final DefectsByPeriodMapper defectsByPeriodMapper;
    private final MergedMrStatsMapper mergedMrStatsMapper;

    @Override
    public ResponseEntity<List<DailyStats>> getDailyStats(LocalDate from, LocalDate to, String email,
                                                          String team) {
        // email/team опциональны и независимы: пусто/blank → без соответствующего фильтра (как hourly).
        Optional<Email> author = (email == null || email.isBlank())
                ? Optional.empty()
                : Optional.of(new Email(email));
        Optional<String> teamFilter = (team == null || team.isBlank())
                ? Optional.empty()
                : Optional.of(team);
        var data = getDailyStats.findByPeriod(new Period(from, to), author, teamFilter).stream()
                .map(dailyStatsMapper::toDto).toList();
        return ResponseEntity.ok(data);
    }

    @Override
    public ResponseEntity<List<WeeklyStats>> getWeeklyStats(LocalDate from, LocalDate to) {
        var data = getWeeklyStats.findByPeriod(new Period(from, to)).stream()
                .map(weeklyStatsMapper::toDto).toList();
        return ResponseEntity.ok(data);
    }

    @Override
    public ResponseEntity<PeriodSummary> getSummaryStats(LocalDate from, LocalDate to) {
        return ResponseEntity.ok(periodSummaryMapper.toDto(
                getPeriodSummary.summarize(new Period(from, to))));
    }

    @Override
    public ResponseEntity<HourlyStats> getHourlyStats(LocalDate from, LocalDate to, String email,
                                                      String team) {
        // email/team опциональны и независимы: пусто/blank → без соответствующего фильтра.
        Optional<Email> author = (email == null || email.isBlank())
                ? Optional.empty()
                : Optional.of(new Email(email));
        Optional<String> teamFilter = (team == null || team.isBlank())
                ? Optional.empty()
                : Optional.of(team);
        return ResponseEntity.ok(hourlyStatsMapper.toDto(
                getHourlyStats.get(new Period(from, to), author, teamFilter)));
    }

    @Override
    public ResponseEntity<ReviewStats> getReviewStats(LocalDate from, LocalDate to) {
        return ResponseEntity.ok(reviewStatsMapper.toDto(
                getReviewStats.get(new Period(from, to))));
    }

    @Override
    public ResponseEntity<PerformanceReview> getPerformanceReview(String email, LocalDate from,
                                                                  LocalDate to, Boolean compareToPrevious) {
        boolean compare = Boolean.TRUE.equals(compareToPrevious);
        return getPerformanceReview.review(new Email(email), new Period(from, to), compare)
                .map(performanceReviewMapper::toDto)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<DefectsByPeriodResponse> getTeamDefects(DefectsByPeriodRequest request) {
        String team = requireTeam(request.getTeam());
        // Каждый PeriodRange → доменный Period (конструктор валидирует to >= from → 400).
        List<Period> periods = request.getPeriods().stream()
                .map(p -> new Period(p.getFrom(), p.getTo()))
                .toList();
        return ResponseEntity.ok(defectsByPeriodMapper.toDto(getTeamDefects.get(team, periods)));
    }

    @Override
    public ResponseEntity<MergedMrStats> getMergedMrStats(LocalDate from, LocalDate to, String team) {
        return ResponseEntity.ok(mergedMrStatsMapper.toDto(
                getMergedMrStats.get(requireTeam(team), new Period(from, to))));
    }

    @Override
    public ResponseEntity<MarkDefectsAiAgentResponse> markDefectsAiAgent(MarkDefectsAiAgentRequest request) {
        List<KaitenCardId> ids = request.getCardIds().stream()
                .map(id -> new KaitenCardId(id))
                .toList();
        AiAgentMarkResult result = markDefectsAiAgent.mark(ids);
        MarkDefectsAiAgentResponse dto = new MarkDefectsAiAgentResponse()
                .requested(result.requested())
                .updated(result.updated())
                .failedIds(result.failedIds().stream().map(KaitenCardId::value).toList());
        return ResponseEntity.ok(dto);
    }

    /** Оба раздела team-scoped: пустая команда — ошибка запроса (400), не «вся компания». */
    private static String requireTeam(String team) {
        if (team == null || team.isBlank()) {
            throw new IllegalArgumentException("team is required");
        }
        return team;
    }
}
