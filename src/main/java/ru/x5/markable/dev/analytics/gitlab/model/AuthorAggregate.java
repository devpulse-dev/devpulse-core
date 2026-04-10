package ru.x5.markable.dev.analytics.gitlab.model;

/**
 * Агрегированная статистика коммитов автора.
 * 
 * <p>Неизменяемая запись (record), содержащая агрегированную информацию о коммитах автора:
 * количество коммитов, количество merge-коммитов, количество добавленных и удаленных строк,
 * количество добавленных строк в тестовых файлах.</p>
 * 
 * <p>Предоставляет методы для создания новых экземпляров с обновленной статистикой,
 * сохраняя неизменяемость (immutability).</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
public record AuthorAggregate(String email,
                               long mergeCommits,
                               long commits,
                               long added,
                               long deleted,
                               long testAdded) {

    /**
     * Создает агрегат с нулевой статистикой для указанного email.
     * 
     * @param email email автора
     */
    public AuthorAggregate(String email) {
        this(email, 0, 0, 0, 0, 0);
    }

    /**
     * Создает новый агрегат с добавленным коммитом.
     * 
     * @param isMerge true, если коммит является merge-коммитом
     * @return новый агрегат с обновленной статистикой коммитов
     */
    public AuthorAggregate addCommit(boolean isMerge) {
        return new AuthorAggregate(
                email,
                mergeCommits + (isMerge ? 1 : 0),
                commits + (isMerge ? 0 : 1),
                added,
                deleted,
                testAdded
        );
    }

    /**
     * Создает новый агрегат с добавленными строками кода.
     * 
     * @param add количество добавленных строк
     * @param del количество удаленных строк
     * @param isTest true, если строки добавлены в тестовый файл
     * @return новый агрегат с обновленной статистикой строк
     */
    public AuthorAggregate addLines(long add, long del, boolean isTest) {
        return new AuthorAggregate(
                email,
                mergeCommits,
                commits,
                added + add,
                deleted + del,
                testAdded + (isTest ? add : 0)
        );
    }

    /**
     * Объединяет статистику с другим агрегатом.
     * 
     * @param other другой агрегат для объединения
     * @return новый агрегат с объединенной статистикой
     */
    public AuthorAggregate merge(AuthorAggregate other) {
        return new AuthorAggregate(
                email,
                mergeCommits + other.mergeCommits,
                commits + other.commits,
                added + other.added,
                deleted + other.deleted,
                testAdded + other.testAdded
        );
    }
}
