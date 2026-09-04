package ru.x5.devpulse.domain.model.performance;

import java.util.List;
import ru.x5.devpulse.domain.model.kaiten.KaitenCardId;
import ru.x5.devpulse.domain.model.kaiten.KaitenCardType;
import ru.x5.devpulse.domain.model.review.AuthoredMergeRequest;

/**
 * Списание на одну карточку внутри дня: на что ушло время, сколько, дефект это или разработка,
 * есть ли галка AI-Agent и какие MR с этой карточкой связаны.
 */
public record TimesheetEntry(
        KaitenCardId cardId,
        String title,
        String url,
        KaitenCardType type,
        boolean aiAgent,
        int minutes,
        List<AuthoredMergeRequest> mergeRequests) {

    public TimesheetEntry {
        mergeRequests = mergeRequests == null ? List.of() : List.copyOf(mergeRequests);
    }
}
