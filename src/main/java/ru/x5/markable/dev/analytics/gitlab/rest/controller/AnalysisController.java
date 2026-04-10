package ru.x5.markable.dev.analytics.gitlab.rest.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.x5.markable.dev.analytics.gitlab.interactor.AnalysisInteractor;
import ru.x5.markable.dev.analytics.gitlab.mapper.AuthorStatsMapper;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.AnalysisRequest;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.AnalysisResponse;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.DailyCommitStatsDto;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.DailyUserStatsDto;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.PeriodSummaryDto;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.WeeklyCommitStatsDto;
import ru.x5.markable.dev.analytics.gitlab.service.DailyStatsService;

/**
 * REST-контроллер для анализа Git-репозиториев и получения статистики.
 * 
 * <p>Предоставляет API для запуска анализа репозиториев, сбора ежедневной статистики
 * и получения различных отчётов по активности разработчиков.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@RestController
@RequestMapping("/api/v1/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    /**
     * Интерактор для запуска анализа репозиториев.
     */
    private final AnalysisInteractor analysisInteractor;

    /**
     * Сервис для работы с ежедневной статистикой.
     */
    private final DailyStatsService dailyStatsService;

    /**
     * Маппер для преобразования статистики авторов в DTO.
     */
    private final AuthorStatsMapper mapper;

    /**
     * Запускает анализ Git-репозиториев.
     * 
     * @param request запрос на анализ с указанием периодов и репозиториев
     * @return список результатов анализа
     */
    @PostMapping
    public ResponseEntity<List<AnalysisResponse>> start(@RequestBody AnalysisRequest request) {
        return ResponseEntity.ok().body(mapper.toDto(analysisInteractor.startAnalysis(request)));
    }

    /**
     * Ручной запуск сбора ежедневной статистики Запускает сбор данных с последней выгрузки до текущего момента
     */
    @PostMapping("/daily/collect")
    public ResponseEntity<String> collectDailyStats() {
        dailyStatsService.collectDailyStats();
        return ResponseEntity.ok("Daily stats collection started successfully");

    }

    /**
     * Получить ежедневную статистику коммитов для графика (все данные)
     */
    @GetMapping("/daily")
    public ResponseEntity<List<DailyCommitStatsDto>> getAllDailyCommits() {
        return ResponseEntity.ok(dailyStatsService.getAllDailyCommits());
    }

    /**
     * Получить ежедневную статистику с детализацией по пользователям (все данные)
     */
    @GetMapping("/daily/detailed")
    public ResponseEntity<List<DailyUserStatsDto>> getAllDailyUserStats() {
        return ResponseEntity.ok(dailyStatsService.getAllDailyUserStats());
    }

    /**
     * Получить суммарную статистику за весь период
     */
    @GetMapping("/summary")
    public ResponseEntity<PeriodSummaryDto> getPeriodSummary() {
        return ResponseEntity.ok(dailyStatsService.getPeriodSummary());
    }

    @GetMapping("/weekly")
    public ResponseEntity<List<WeeklyCommitStatsDto>> getWeeklyCommits() {
        return ResponseEntity.ok(dailyStatsService.getWeeklyCommits());
    }
}
