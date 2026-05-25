package ru.x5.markable.dev.analytics.application.port.out;

import java.util.Collection;
import java.util.List;
import ru.x5.markable.dev.analytics.domain.common.Period;
import ru.x5.markable.dev.analytics.domain.model.kaiten.KaitenCard;
import ru.x5.markable.dev.analytics.domain.model.kaiten.KaitenCardId;
import ru.x5.markable.dev.analytics.domain.model.user.KaitenUserId;

/**
 * Port out: персистентность карточек Kaiten.
 */
public interface KaitenCardRepository {

    /** Bulk upsert — карточка опознаётся по {@link KaitenCardId}. */
    void upsertAll(Collection<KaitenCard> cards);

    /** Карточки, в которых пользователь участвует, обновлённые за период. */
    List<KaitenCard> findByMemberAndPeriod(KaitenUserId memberId, Period period);
}
