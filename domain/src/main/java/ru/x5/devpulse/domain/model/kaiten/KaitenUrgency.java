package ru.x5.devpulse.domain.model.kaiten;

/**
 * Срочность карточки Kaiten. Маппится по значению property {@code id_2561}
 * (в API приходит массивом, например {@code "id_2561":[4524]} — берём первый элемент).
 *
 * <p>Известна в основном для дефектов; у разработки/задач обычно отсутствует → {@link #UNKNOWN}.</p>
 */
public enum KaitenUrgency {
    CRITICAL(4523),
    HIGH(4524),
    MEDIUM(4525),
    LOW(4526),
    /** Не задана / неизвестный код. */
    UNKNOWN(-1);

    private final int id;

    KaitenUrgency(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    /** Преобразует raw код срочности в enum. {@code null}/неизвестный → {@link #UNKNOWN}. */
    public static KaitenUrgency fromId(Integer id) {
        if (id == null) return UNKNOWN;
        for (KaitenUrgency u : values()) {
            if (u.id == id) return u;
        }
        return UNKNOWN;
    }
}
