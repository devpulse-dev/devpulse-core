package ru.x5.markable.dev.analytics.gitlab.rest.dto;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Getter;

/**
 * Перечисление статусов карточек Kaiten.
 * 
 * <p>Определяет возможные статусы карточек в системе Kaiten и предоставляет методы
 * для определения статуса по идентификатору колонки или коду. Также содержит метод
 * для проверки, является ли статус закрытым.</p>
 * 
 * <p>Статусы включают: Новый/Переоткрыт, Подтверждено, На ревью, Ревью/Подтверждён,
 * В работе, В уточнении, Проверить в тесте, Проверить в проде, Готово, Выполнено,
 * Очередь и Неизвестно.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@Getter
public enum KaitenCardStatus {

    /**
     * Новый/Переоткрыт.
     * 
     * <p>Статус для новых или переоткрытых карточек.</p>
     */
    NEW_REOPENED("Новый/Переоткрыт", "Новый/Переоткрыт", 40338, 64177),
    
    /**
     * Подтверждено.
     * 
     * <p>Статус для подтверждённых карточек.</p>
     */
    CONFIRMED("Подтверждено", "Подтверждено", 40346),
    
    /**
     * На ревью.
     * 
     * <p>Статус для карточек на ревью.</p>
     */
    IN_REVIEW("На ревью", "На ревью", 40345),
    
    /**
     * Ревью, Подтверждён.
     * 
     * <p>Статус для карточек, прошедших ревью и подтверждённых.</p>
     */
    REVIEW_CONFIRMED("Ревью, Подтверждён", "Ревью, Подтверждён", 70962),
    
    /**
     * В работе.
     * 
     * <p>Статус для карточек в работе.</p>
     */
    IN_PROGRESS("В работе", "В работе", 40344, 64179),
    
    /**
     * В уточнении.
     * 
     * <p>Статус для карточек в уточнении.</p>
     */
    IN_CLARIFICATION("В уточнении", "В уточнении", 40340),
    
    /**
     * Проверить в тесте.
     * 
     * <p>Статус для карточек, требующих проверки в тестовой среде.</p>
     */
    TEST_CHECK("Проверить в тесте", "Проверить в тесте", 53748, 64184),
    
    /**
     * Проверить в проде.
     * 
     * <p>Статус для карточек, требующих проверки в продакшн среде.</p>
     */
    PROD_CHECK("Проверить в проде", "Проверить в проде", 53749),
    
    /**
     * Готово.
     * 
     * <p>Статус для готовых карточек.</p>
     */
    DONE("Готово", "Готово", 40343, 64187),
    
    /**
     * Выполнено.
     * 
     * <p>Статус для выполненных карточек.</p>
     */
    COMPLETED("Выполнено", "Выполнено", 70964),
    
    /**
     * Очередь.
     * 
     * <p>Статус для карточек в очереди.</p>
     */
    QUEUE("Очередь", "Очередь", 70960),
    
    /**
     * Неизвестно.
     * 
     * <p>Статус для карточек с неизвестным статусом.</p>
     */
    UNKNOWN("Неизвестно", "Неизвестно");

    /**
     * Код статуса.
     * 
     * <p>Уникальный код статуса.</p>
     */
    private final String code;
    
    /**
     * Отображаемое имя статуса.
     * 
     * <p>Имя статуса для отображения в пользовательском интерфейсе.</p>
     */
    private final String displayName;
    
    /**
     * Идентификаторы колонок.
     * 
     * <p>Массив идентификаторов колонок, соответствующих данному статусу.</p>
     */
    private final long[] columnIds;

    /**
     * Конструктор перечисления.
     * 
     * @param код статуса
     * @param displayName отображаемое имя статуса
     * @param columnIds идентификаторы колонок
     */
    KaitenCardStatus(String code, String displayName, long... columnIds) {
        this.code = code;
        this.displayName = displayName;
        this.columnIds = columnIds;
    }

    /**
     * Карта статусов по идентификатору колонки.
     * 
     * <p>Статическая карта для быстрого поиска статуса по идентификатору колонки.</p>
     */
    private static final Map<Long, KaitenCardStatus> STATUS_BY_COLUMN_ID = Arrays.stream(values())
            .filter(status -> status.columnIds != null)
            .flatMap(status -> Arrays.stream(status.columnIds)
                    .mapToObj(columnId -> Map.entry(columnId, status)))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    /**
     * Получает статус по идентификатору колонки.
     * 
     * @param columnId идентификатор колонки
     * @return статус карточки или {@link #UNKNOWN}, если статус не найден
     */
    public static KaitenCardStatus fromColumnId(Long columnId) {
        if (columnId == null) {
            return UNKNOWN;
        }
        return STATUS_BY_COLUMN_ID.getOrDefault(columnId, UNKNOWN);
    }

    /**
     * Получает статус по коду.
     * 
     * @param code код статуса
     * @return статус карточки или {@link #UNKNOWN}, если статус не найден
     */
    public static KaitenCardStatus fromCode(String code) {
        if (code == null) {
            return UNKNOWN;
        }
        return Arrays.stream(values())
                .filter(status -> status.code.equals(code))
                .findFirst()
                .orElse(UNKNOWN);
    }

    /**
     * Проверяет, является ли статус закрытым.
     * 
     * <p>Закрытыми считаются статусы: {@link #DONE}, {@link #COMPLETED},
     * {@link #PROD_CHECK}, {@link #TEST_CHECK}.</p>
     * 
     * @return {@code true}, если статус закрытый, иначе {@code false}
     */
    public boolean isClosed() {
        return this == DONE || this == COMPLETED || this == PROD_CHECK || this == TEST_CHECK;
    }
}
