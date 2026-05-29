package ru.x5.devpulse.domain.common;

import java.util.List;
import java.util.Objects;

/**
 * Иммутабельная страница результатов с метаданными пагинации.
 *
 * <p>Доменный аналог Spring {@code Page} — не тащим Spring-типы наверх в use case API.</p>
 *
 * @param items         элементы текущей страницы
 * @param page          0-based номер запрошенной страницы
 * @param size          запрошенный размер страницы
 * @param totalElements общее число элементов во всём результате (без пагинации)
 */
public record Page<T>(
        List<T> items,
        int page,
        int size,
        long totalElements
) {

    public Page {
        Objects.requireNonNull(items, "items required");
        items = List.copyOf(items);
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0, got " + page);
        }
        if (size < 1) {
            throw new IllegalArgumentException("size must be >= 1, got " + size);
        }
        if (totalElements < 0) {
            throw new IllegalArgumentException("totalElements must be >= 0, got " + totalElements);
        }
    }

    public int totalPages() {
        if (totalElements == 0) return 0;
        return (int) ((totalElements + size - 1) / size);
    }

    public boolean hasNext() {
        return (long) (page + 1) * size < totalElements;
    }

    /** Создать страницу из полного отсортированного списка применением page/size offset. */
    public static <T> Page<T> of(List<T> all, PageRequest request) {
        int from = Math.min(request.offset(), all.size());
        int to = Math.min(from + request.size(), all.size());
        return new Page<>(all.subList(from, to), request.page(), request.size(), all.size());
    }
}
