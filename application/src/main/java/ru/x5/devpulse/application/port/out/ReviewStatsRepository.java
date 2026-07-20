package ru.x5.devpulse.application.port.out;

import java.util.Collection;
import java.util.List;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.review.MergeRequest;
import ru.x5.devpulse.domain.model.review.MergedMrCountRow;
import ru.x5.devpulse.domain.model.review.RepoMergedMrCount;
import ru.x5.devpulse.domain.model.user.Email;

/**
 * Port out: чтение MR с участием ревьюеров для агрегации ревью-метрик.
 *
 * <p>Запись MR/ревью — задача collection-фазы (сбор из GitLab), отдельный порт.
 * Здесь только read-side для {@code GET /stats/reviews} и {@code GET /stats/merged-mrs}.</p>
 */
public interface ReviewStatsRepository {

    /**
     * MR, открытые в указанном периоде (по {@code created_at}), вместе с участием
     * ревьюеров. Период — закрытый интервал дат.
     */
    List<MergeRequest> findMergeRequestsByPeriod(Period period);

    /**
     * Кол-во вмерженных MR по авторам за период: {@code merged_at} внутри периода,
     * автор ∈ {@code authorEmails}. Агрегация (GROUP BY author) — на стороне БД.
     *
     * <p>Возвращает только авторов с хотя бы одним MR. При пустом {@code authorEmails}
     * возвращает пустой список (вызывающий не должен слать пустой IN).</p>
     */
    List<MergedMrCountRow> countMergedMrByAuthor(
            Period period, Collection<Email> authorEmails, Collection<String> targetBranches);

    /**
     * Кол-во вмерженных MR по репозиториям за период (тот же фильтр по авторам команды и веткам).
     * Имя репозитория выводится из {@code web_url} MR (агрегация по {@code gitlab_project_id}).
     *
     * <p>Тот же {@code total}, что и {@link #countMergedMrByAuthor}, только сгруппированный иначе.
     * Пустой {@code authorEmails} или {@code targetBranches} → пустой список.</p>
     */
    List<RepoMergedMrCount> countMergedMrByRepo(
            Period period, Collection<Email> authorEmails, Collection<String> targetBranches);
}
