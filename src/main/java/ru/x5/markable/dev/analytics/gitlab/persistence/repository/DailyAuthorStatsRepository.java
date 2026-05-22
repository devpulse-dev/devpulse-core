package ru.x5.markable.dev.analytics.gitlab.persistence.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.x5.markable.dev.analytics.gitlab.persistence.entity.DailyAuthorStats;

/**
 * Репозиторий для работы с сущностью {@link DailyAuthorStats}.
 * 
 * <p>Предоставляет методы для выполнения CRUD операций над записями о ежедневной статистике авторов,
 * а также специализированные методы для поиска и фильтрации данных по различным критериям.</p>
 * 
 * <p>Использует Long в качестве идентификатора сущности.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 * @see DailyAuthorStats
 * @see JpaRepository
 */
@Repository
public interface DailyAuthorStatsRepository extends JpaRepository<DailyAuthorStats, Long> {

    /**
     * Находит записи о ежедневной статистике авторов за указанный период.
     * 
     * @param start начальная дата периода (включительно)
     * @param end конечная дата периода (включительно)
     * @return список записей о ежедневной статистике авторов за указанный период
     */
    List<DailyAuthorStats> findByDateBetween(LocalDate start, LocalDate end);

    /**
     * Находит записи о ежедневной статистике авторов за указанную дату.
     * 
     * @param date дата для поиска
     * @return список записей о ежедневной статистике авторов за указанную дату
     */
    List<DailyAuthorStats> findByDate(LocalDate date);

    /**
     * Находит записи о ежедневной статистике авторов по списку email и дате.
     * 
     * @param emails список email авторов
     * @param date дата для поиска
     * @return список записей о ежедневной статистике авторов
     */
    List<DailyAuthorStats> findByEmailInAndDate(List<String> emails, LocalDate date);

    /**
     * Удаляет записи о ежедневной статистике авторов старше указанной даты.
     * 
     * @param date дата, записи старше которой будут удалены
     * @return количество удалённых записей
     */
    @Modifying
    @Query("DELETE FROM DailyAuthorStats d WHERE d.date < :date")
    int deleteOlderThan(@Param("date") LocalDate date);

    /**
     * Находит записи о ежедневной статистике авторов по email.
     * 
     * @param email email автора
     * @return список записей о ежедневной статистике автора
     */
    List<DailyAuthorStats> findByEmail(String email);

    /**
     * Находит записи о ежедневной статистике авторов по email, отсортированные по дате по возрастанию.
     * 
     * @param email email автора
     * @return список записей о ежедневной статистике автора, отсортированный по дате по возрастанию
     */
    List<DailyAuthorStats> findByEmailOrderByDateAsc(String email);

    /**
     * Находит запись о ежедневной статистике автора по email, дате и названию репозитория.
     * 
     * @param email email автора
     * @param date дата
     * @param repositoryName название репозитория
     * @return запись о ежедневной статистике автора или {@link Optional#empty()}, если запись не найдена
     */
    Optional<DailyAuthorStats> findByEmailAndDateAndRepositoryName(String email, LocalDate date, String repositoryName);

    /**
     * Находит уникальные названия репозиториев по email автора.
     * 
     * @param email email автора
     * @return список уникальных названий репозиториев
     */
    @Query("SELECT DISTINCT d.repositoryName FROM DailyAuthorStats d WHERE d.email = :email")
    List<String> findRepositoriesByEmail(@Param("email") String email);

    /**
     * Находит записи о ежедневной статистике автора по email и периоду, отсортированные по дате по возрастанию.
     * 
     * @param email email автора
     * @param start начальная дата периода (включительно)
     * @param end конечная дата периода (включительно)
     * @return список записей о ежедневной статистике автора за указанный период
     */
    @Query("SELECT d FROM DailyAuthorStats d WHERE d.email = :email AND d.date BETWEEN :start AND :end ORDER BY d.date ASC")
    List<DailyAuthorStats> findByEmailAndDateBetween(
            @Param("email") String email,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);

    /**
     * Находит уникальные названия репозиториев по email автора и периоду.
     *
     * @param email email автора
     * @param start начальная дата периода (включительно)
     * @param end конечная дата периода (включительно)
     * @return список уникальных названий репозиториев за указанный период
     */
    @Query("SELECT DISTINCT d.repositoryName FROM DailyAuthorStats d WHERE d.email = :email AND d.date BETWEEN :start AND :end")
    List<String> findRepositoriesByEmailAndPeriod(
            @Param("email") String email,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end
    );

    /**
     * Возвращает существующие записи статистики для репозитория за период.
     * Используется при batch-сохранении, чтобы определить какие записи обновить,
     * а какие вставить — одним SELECT'ом вместо N findByEmailAndDateAndRepositoryName.
     */
    @Query("SELECT d FROM DailyAuthorStats d " +
            "WHERE d.repositoryName = :repoName AND d.date BETWEEN :start AND :end")
    List<DailyAuthorStats> findByRepoAndDateBetween(
            @Param("repoName") String repoName,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);
}
