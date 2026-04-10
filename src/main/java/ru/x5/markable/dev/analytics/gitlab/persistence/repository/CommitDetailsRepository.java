package ru.x5.markable.dev.analytics.gitlab.persistence.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.x5.markable.dev.analytics.gitlab.persistence.entity.CommitDetails;

/**
 * Репозиторий для работы с сущностью {@link CommitDetails}.
 * 
 * <p>Предоставляет методы для выполнения CRUD операций над записями о деталях коммитов,
 * а также специализированные методы для поиска по различным критериям, анализа активности
 * и управления устаревшими данными.</p>
 * 
 * <p>Использует Long в качестве идентификатора сущности.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 * @see CommitDetails
 * @see JpaRepository
 */
@Repository
public interface CommitDetailsRepository extends JpaRepository<CommitDetails, Long> {

    /**
     * Находит коммиты по электронной почте автора, отсортированные по дате коммита в возрастающем порядке.
     * 
     * @param email электронная почта автора
     * @return список коммитов автора, отсортированный по дате
     */
    List<CommitDetails> findByEmailOrderByCommitDateAsc(String email);

    /**
     * Находит коммиты по электронной почте автора и дате коммита в указанном временном интервале.
     * 
     * @param email электронная почта автора
     * @param start начало временного интервала
     * @param end конец временного интервала
     * @return список коммитов автора в указанном интервале
     */
    List<CommitDetails> findByEmailAndCommitDateBetween(String email, LocalDateTime start, LocalDateTime end);

    /**
     * Проверяет существование коммита с указанным хешем.
     * 
     * @param commitHash хеш коммита
     * @return true если коммит существует, false в противном случае
     */
    boolean existsByCommitHash(String commitHash);

    /**
     * Находит коммит по хешу.
     * 
     * @param commitHash хеш коммита
     * @return Optional с найденным коммитом, или пустой Optional если коммит не найден
     */
    Optional<CommitDetails> findByCommitHash(String commitHash);

    /**
     * Находит почасовую активность автора по электронной почте.
     * 
     * <p>Использует JPQL запрос для группировки коммитов по часу и подсчета их количества.
     * Результаты сортируются по часу в возрастающем порядке.</p>
     * 
     * @param email электронная почта автора
     * @return список массивов объектов, где первый элемент - час, второй - количество коммитов
     */
    @Query("SELECT c.hour, COUNT(c) FROM CommitDetails c WHERE c.email = :email GROUP BY c.hour ORDER BY c.hour")
    List<Object[]> findHourlyActivityByEmail(@Param("email") String email);

    /**
     * Находит существующие хеши коммитов из указанного списка.
     * 
     * <p>Использует JPQL запрос для выборки хешей коммитов, которые существуют в базе данных.</p>
     * 
     * @param hashes список хешей коммитов для проверки
     * @return список существующих хешей коммитов
     */
    @Query("SELECT c.commitHash FROM CommitDetails c WHERE c.commitHash IN :hashes")
    List<String> findExistingHashes(@Param("hashes") List<String> hashes);

    /**
     * Удаляет коммиты, созданные до указанной даты.
     * 
     * <p>Используется для очистки устаревших данных и управления размером базы данных.</p>
     * 
     * @param date дата, до которой удаляются коммиты
     */
    void deleteByCommitDateBefore(LocalDateTime date);
}
