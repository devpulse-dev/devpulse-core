package ru.x5.markable.dev.analytics.domain.model.stats;

/**
 * Категория активности разработчика на основе {@link ActivityScore#score()}.
 *
 * <p>Используется фронтом для бейджа на карточке («крутыш», «ниже нормы», «не активен»).</p>
 */
public enum ActivityCategory {
    /** Меньше 20% от ожидаемой нормы — практически нет работы. */
    INACTIVE,
    /** 20–60% от нормы — работает, но мало. */
    BELOW_AVERAGE,
    /** 60–150% от нормы — здоровый темп. */
    ACTIVE,
    /** Выше 150% — топ. */
    STAR
}
