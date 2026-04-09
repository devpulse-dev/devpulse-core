package ru.x5.markable.dev.analytics.gitlab.rest.dto;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
public enum KaitenCardStatus {

    NEW_REOPENED("Новый/Переоткрыт", "Новый/Переоткрыт", 40338, 64177),
    CONFIRMED("Подтверждено", "Подтверждено", 40346),
    IN_REVIEW("На ревью", "На ревью", 40345),
    REVIEW_CONFIRMED("Ревью, Подтверждён", "Ревью, Подтверждён", 70962),
    IN_PROGRESS("В работе", "В работе", 40344, 64179),
    IN_CLARIFICATION("В уточнении", "В уточнении", 40340),
    TEST_CHECK("Проверить в тесте", "Проверить в тесте", 53748, 64184),
    PROD_CHECK("Проверить в проде", "Проверить в проде", 53749),
    DONE("Готово", "Готово", 40343, 64187),
    COMPLETED("Выполнено", "Выполнено", 70964),
    QUEUE("Очередь", "Очередь", 70960),
    UNKNOWN("Неизвестно", "Неизвестно");

    private final String code;
    private final String displayName;
    private final long[] columnIds;

    KaitenCardStatus(String code, String displayName, long... columnIds) {
        this.code = code;
        this.displayName = displayName;
        this.columnIds = columnIds;
    }

    private static final Map<Long, KaitenCardStatus> STATUS_BY_COLUMN_ID = Arrays.stream(values())
            .filter(status -> status.columnIds != null)
            .flatMap(status -> Arrays.stream(status.columnIds)
                    .mapToObj(columnId -> Map.entry(columnId, status)))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    public static KaitenCardStatus fromColumnId(Long columnId) {
        if (columnId == null) {
            return UNKNOWN;
        }
        return STATUS_BY_COLUMN_ID.getOrDefault(columnId, UNKNOWN);
    }

    public static KaitenCardStatus fromCode(String code) {
        if (code == null) {
            return UNKNOWN;
        }
        return Arrays.stream(values())
                .filter(status -> status.code.equals(code))
                .findFirst()
                .orElse(UNKNOWN);
    }

    public boolean isClosed() {
        return this == DONE || this == COMPLETED || this == PROD_CHECK || this == TEST_CHECK;
    }
}
