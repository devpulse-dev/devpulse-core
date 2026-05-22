package ru.x5.markable.dev.analytics.domain.common;

/**
 * Запрос страницы: 0-based номер и ограниченный размер.
 *
 * <p>Сделан явным в domain, чтобы не таскать Spring-овый
 * {@code org.springframework.data.domain.Pageable} в use case API.</p>
 */
public record PageRequest(int page, int size) {

    public static final int MAX_SIZE = 500;
    public static final PageRequest FIRST_PAGE = new PageRequest(0, 50);

    public PageRequest {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0, got " + page);
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("size must be in [1.." + MAX_SIZE + "], got " + size);
        }
    }

    public int offset() {
        return page * size;
    }
}
