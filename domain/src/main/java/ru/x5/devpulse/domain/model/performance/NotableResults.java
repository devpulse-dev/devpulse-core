package ru.x5.devpulse.domain.model.performance;

import java.util.List;

/**
 * Заметные результаты за период — два осмысленных среза вместо плоского списка:
 *
 * <ul>
 *   <li>{@code firefighting} — закрытые критичные/высокие дефекты («тушение пожаров»);</li>
 *   <li>{@code deliveredFeatures} — корневые задачи с завершёнными юскейсами («доставленные доработки»).</li>
 * </ul>
 *
 * <p>Строится только на надёжных структурных сигналах (срочность, завершённость, уровень фичи).
 * member.type («ответственный») и time_spent сознательно не используются — первый = владелец/создатель
 * карточки (не исполнитель), второй заполняется руками и почти всегда near-zero.</p>
 */
public record NotableResults(List<FirefightingItem> firefighting, List<DeliveredFeature> deliveredFeatures) {

    public static final NotableResults EMPTY = new NotableResults(List.of(), List.of());

    public NotableResults {
        firefighting = firefighting == null ? List.of() : List.copyOf(firefighting);
        deliveredFeatures = deliveredFeatures == null ? List.of() : List.copyOf(deliveredFeatures);
    }
}
