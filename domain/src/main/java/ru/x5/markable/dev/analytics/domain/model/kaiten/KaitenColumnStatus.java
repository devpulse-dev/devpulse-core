package ru.x5.markable.dev.analytics.domain.model.kaiten;

/**
 * Статус колонки на доске Kaiten (поле {@code column.type} в API).
 *
 * <p>Используется для определения статуса карточки независимо от названия колонки
 * («В уточнении», «Готово к ревью», «Done» и т.д. — конкретная колонка зависит от доски,
 * но её тип — стандартный для всех досок).</p>
 */
public enum KaitenColumnStatus {
    /** {@code type=1} — новые / TODO. */
    NEW(1),
    /** {@code type=2} — в работе. */
    IN_PROGRESS(2),
    /** {@code type=3} — завершено. */
    DONE(3),
    /** Любые нестандартные / неизвестные типы колонок. */
    UNKNOWN(-1);

    private final int type;

    KaitenColumnStatus(int type) {
        this.type = type;
    }

    public int type() {
        return type;
    }

    /** Преобразует raw {@code column.type} в enum. {@code null} → {@link #UNKNOWN}. */
    public static KaitenColumnStatus fromType(Integer columnType) {
        if (columnType == null) return UNKNOWN;
        for (KaitenColumnStatus s : values()) {
            if (s.type == columnType) return s;
        }
        return UNKNOWN;
    }
}
