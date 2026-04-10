package ru.x5.markable.dev.analytics.gitlab.service.impl.helper;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.CommitDetailDto;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.KaitenCardStatus;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.KaitenCardWithCommitsDto;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.TaskWithCommitsDto;
import ru.x5.markable.dev.analytics.kaiten.persistence.entity.KaitenCard;
import ru.x5.markable.dev.analytics.kaiten.service.KaitenCardMemberService;
import ru.x5.markable.dev.analytics.kaiten.service.KaitenCardService;

/**
 * Вспомогательный класс для получения карточек Kaiten пользователя.
 * Отвечает за извлечение и фильтрацию карточек с привязкой к коммитам.
 */
@Component
@Log4j2
@RequiredArgsConstructor
public class KaitenCardFetcher {

    private final KaitenCardMemberService kaitenCardMemberService;
    private final KaitenCardService kaitenCardService;

    /**
     * Получает карточки Kaiten пользователя за указанный период.
     * Фильтрует карточки по статусу и наличию коммитов.
     *
     * @param kaitenUserId ID пользователя в Kaiten
     * @param taskWithCommitsDtos список задач с коммитами
     * @param periodStart начало периода (может быть null)
     * @param periodEnd конец периода (может быть null)
     * @return список карточек с коммитами
     */
    public List<KaitenCardWithCommitsDto> fetchKaitenCards(
            Long kaitenUserId,
            List<TaskWithCommitsDto> taskWithCommitsDtos,
            LocalDate periodStart,
            LocalDate periodEnd
    ) {
        if (kaitenUserId == null) {
            log.debug("User has no kaiten_id");
            return Collections.emptyList();
        }

        // 1. Получаем ID карточек пользователя
        List<Long> cardIds = kaitenCardMemberService.getCardIdsByUserId(kaitenUserId);
        if (cardIds.isEmpty()) {
            log.debug("User {} has no Kaiten cards in period", kaitenUserId);
            return Collections.emptyList();
        }

        // 2. Получаем карточки по ID
        List<KaitenCard> cards = kaitenCardService.findByIds(cardIds);

        // 3. Группируем коммиты по ID карточки (извлекаем из task_number)
        List<CommitDetailDto> commits = taskWithCommitsDtos.stream()
                .map(TaskWithCommitsDto::getCommits)
                .flatMap(List::stream)
                .toList();

        Map<Long, List<CommitDetailDto>> commitsByCardId = commits.stream()
                .filter(c -> c.getTaskNumber() != null && !c.getTaskNumber().isBlank())
                .collect(Collectors.groupingBy(
                        commit -> extractKaitenCardIdFromTaskNumber(commit.getTaskNumber()),
                        Collectors.toList()
                ));

        // 4. Фильтруем карточки: открытые ИЛИ есть коммиты
        cards = cards.stream()
                .filter(card -> shouldIncludeCard(card, commitsByCardId, periodStart, periodEnd))
                .toList();

        // 5. Собираем DTO
        return cards.stream()
                .map(card -> buildCardDto(card, commitsByCardId))
                .toList();
    }

    /**
     * Определяет, следует ли включить карточку в результат.
     * Карточка включается, если она находится в периоде И (открыта ИЛИ есть коммиты).
     *
     * @param card карточка Kaiten
     * @param commitsByCardId карта коммитов по ID карточки
     * @param periodStart начало периода
     * @param periodEnd конец периода
     * @return true если карточку следует включить
     */
    private boolean shouldIncludeCard(KaitenCard card, Map<Long, List<CommitDetailDto>> commitsByCardId,
            LocalDate periodStart, LocalDate periodEnd) {
        LocalDate createdDate = card.getCreatedAt().toLocalDate();

        // 1. Проверка периода
        boolean isInPeriod = (periodStart == null ||
                (periodEnd == null || !createdDate.isAfter(periodEnd)));

        // 2. Проверка статуса
        KaitenCardStatus status = KaitenCardStatus.fromColumnId(card.getColumnId());
        boolean isOpen = !status.isClosed();

        // 3. Проверка наличия коммитов
        boolean hasCommits = commitsByCardId.containsKey(card.getId());

        return isInPeriod && (isOpen || hasCommits);
    }

    /**
     * Строит DTO карточки с коммитами.
     *
     * @param card карточка Kaiten
     * @param commitsByCardId карта коммитов по ID карточки
     * @return DTO карточки с коммитами
     */
    private KaitenCardWithCommitsDto buildCardDto(KaitenCard card, Map<Long, List<CommitDetailDto>> commitsByCardId) {
        List<CommitDetailDto> cardCommits = commitsByCardId.getOrDefault(card.getId(), Collections.emptyList());

        return KaitenCardWithCommitsDto.builder()
                .id(card.getId())
                .title(card.getTitle())
                .status(KaitenCardStatus.fromColumnId(card.getColumnId()).getDisplayName())
                .priority(card.getPriority())
                .createdAt(card.getCreatedAt())
                .closedAt(card.getClosedAt())
                .lastMovedAt(card.getLastMovedAt())
                .url(card.getUrl())
                .commits(cardCommits)
                .build();
    }

    /**
     * Извлекает ID карточки Kaiten из номера задачи.
     * Пример: "1700-2423436" -> 2423436
     *
     * @param taskNumber номер задачи
     * @return ID карточки или null если не удалось извлечь
     */
    private Long extractKaitenCardIdFromTaskNumber(String taskNumber) {
        if (taskNumber == null || taskNumber.isBlank()) {
            return null;
        }

        // Ищем цифры после дефиса
        int lastDashIndex = taskNumber.lastIndexOf('-');
        if (lastDashIndex != -1 && lastDashIndex < taskNumber.length() - 1) {
            try {
                return Long.parseLong(taskNumber.substring(lastDashIndex + 1));
            } catch (NumberFormatException e) {
                // Не удалось распарсить
            }
        }

        // Если дефиса нет, пробуем распарсить всё число
        try {
            return Long.parseLong(taskNumber);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
