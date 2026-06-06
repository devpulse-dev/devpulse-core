package ru.x5.devpulse.domain.model.performance;

import java.util.List;

/**
 * Разработка субъекта за период, агрегированная по корневой задаче.
 *
 * @param useCaseCount  всего карточек-юскейсов (разработка + задачи)
 * @param rootTaskCount число уникальных реальных корневых задач (без синтетического ведра)
 * @param roots         корневые задачи с юскейсами; синтетическое «Без корневой задачи» — последним
 */
public record DevelopmentRollup(int useCaseCount, int rootTaskCount, List<RootTask> roots) {

    public static final DevelopmentRollup EMPTY = new DevelopmentRollup(0, 0, List.of());

    public DevelopmentRollup {
        roots = roots == null ? List.of() : List.copyOf(roots);
    }
}
