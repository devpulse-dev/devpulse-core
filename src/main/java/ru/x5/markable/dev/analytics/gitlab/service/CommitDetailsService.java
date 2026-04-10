package ru.x5.markable.dev.analytics.gitlab.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import ru.x5.markable.dev.analytics.gitlab.model.CommitDetail;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.CommitDetailDto;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.TaskWithCommitsDto;

/**
 * Сервис для работы с детальной информацией о коммитах.
 * 
 * <p>Предоставляет функциональность для сохранения, получения и анализа
 * детальной информации о коммитах, включая почасовую активность и группировку по задачам.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
public interface CommitDetailsService {

    /**
     * Сохраняет детальную информацию о коммитах.
     * 
     * @param commits список коммитов для сохранения
     */
    void saveCommitDetails(List<CommitDetail> commits);

    /**
     * Получает все коммиты пользователя по email.
     * 
     * @param email email пользователя
     * @return список DTO с информацией о коммитах
     */
    List<CommitDetailDto> getUserCommits(String email);

    /**
     * Получает почасовую активность пользователя.
     * 
     * <p>Возвращает карту, где ключ - час дня (0-23), а значение - количество коммитов.</p>
     * 
     * @param email email пользователя
     * @return карта почасовой активности
     */
    Map<Integer, Long> getHourlyActivity(String email);

    /**
     * Проверяет существование коммита по хешу.
     * 
     * @param commitHash хеш коммита
     * @return true, если коммит существует, иначе false
     */
    boolean commitExists(String commitHash);

    /**
     * Получает задачи с коммитами пользователя.
     * 
     * <p>Группирует коммиты по номерам задач и возвращает список задач
     * с соответствующими коммитами.</p>
     * 
     * @param email email пользователя
     * @return список задач с коммитами
     */
    List<TaskWithCommitsDto> getTasksWithCommits(String email);

    /**
     * Получает почасовую активность пользователя за период.
     * 
     * <p>Возвращает карту, где ключ - час дня (0-23), а значение - количество коммитов.</p>
     * 
     * @param email email пользователя
     * @param start начало периода
     * @param end конец периода
     * @return карта почасовой активности за период
     */
    Map<Integer, Long> getHourlyActivity(String email, LocalDate start, LocalDate end);

    /**
     * Получает задачи с коммитами пользователя за период.
     * 
     * <p>Группирует коммиты по номерам задач и возвращает список задач
     * с соответствующими коммитами за указанный период.</p>
     * 
     * @param email email пользователя
     * @param start начало периода
     * @param end конец периода
     * @return список задач с коммитами за период
     */
    List<TaskWithCommitsDto> getTasksWithCommits(String email, LocalDate start, LocalDate end);

    /**
     * Получает коммиты пользователя за период.
     * 
     * @param email email пользователя
     * @param start начало периода
     * @param end конец периода
     * @return список DTO с информацией о коммитах за период
     */
    List<CommitDetailDto> getUserCommits(String email, LocalDate start, LocalDate end);

}
