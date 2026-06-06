package ru.x5.devpulse.domain.model.kaiten;

/**
 * Тип карточки Kaiten. Маппится по {@code type_id} (см. API Kaiten).
 *
 * <p>Известные типы — пополняем по мере обнаружения новых:</p>
 * <ul>
 *   <li>{@code 70} — разработка</li>
 *   <li>{@code 8}  — дефект</li>
 *   <li>{@code 6}  — задача (в perf-review учитывается вместе с разработкой)</li>
 * </ul>
 */
public enum KaitenCardType {
    DEVELOPMENT(70),
    DEFECT(8),
    TASK(6),
    /** Неизвестный/новый тип, который мы ещё не классифицировали. */
    OTHER(-1);

    /** «Строящая» работа (не дефект): разработка или задача — идут в один rollup perf-review. */
    public boolean isBuildWork() {
        return this == DEVELOPMENT || this == TASK;
    }

    private final int id;

    KaitenCardType(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    /** Преобразует raw {@code type_id} из Kaiten API в enum. {@code null} → {@link #OTHER}. */
    public static KaitenCardType fromId(Integer typeId) {
        if (typeId == null) return OTHER;
        for (KaitenCardType t : values()) {
            if (t.id == typeId) return t;
        }
        return OTHER;
    }
}
