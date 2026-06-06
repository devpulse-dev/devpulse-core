package ru.x5.devpulse.domain.model.performance;

import java.util.List;

/**
 * Корневая (родительская) задача с её юскейсами — узел аккордеона в блоке разработки.
 *
 * @param id {@code null} — синтетическое ведро «Без корневой задачи» (карточки без родителя)
 */
public record RootTask(Long id, String title, String url, List<UseCaseRef> useCases) {

    public RootTask {
        useCases = useCases == null ? List.of() : List.copyOf(useCases);
    }

    public int useCaseCount() {
        return useCases.size();
    }

    public boolean isUngrouped() {
        return id == null;
    }

    public static RootTask ungrouped(List<UseCaseRef> useCases) {
        return new RootTask(null, "Без корневой задачи", null, useCases);
    }
}
