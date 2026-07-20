package ru.x5.devpulse.domain.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.kaiten.KaitenCard;
import ru.x5.devpulse.domain.model.kaiten.KaitenCardType;
import ru.x5.devpulse.domain.model.performance.PeriodDefectCounts;
import ru.x5.devpulse.domain.model.performance.UrgencyCounts;

/**
 * Чистая логика: раскладка уникальных дефектов по периодам и приоритету (срочности).
 *
 * <p>Дефект — карточка {@code cardType == DEFECT}. Попадание в период определяется по
 * {@code createdAt} («дефект заведён в периоде») — в отличие от perf-review, где важен факт
 * закрытия. Один дефект может попасть в несколько пересекающихся периодов (это осознанный
 * выбор: периоды задаёт пользователь, они независимы). Stateless, без I/O.</p>
 */
public final class DefectPeriodAssembler {

    private DefectPeriodAssembler() {}

    /**
     * По каждому периоду считает дефекты с {@code createdAt} внутри него, группируя по срочности.
     *
     * @param uniqueCards карточки без дубликатов (дедуп по id уже выполнен вызывающим); типы любые —
     *                    не-дефекты и карточки без {@code createdAt} игнорируются
     * @param periods     периоды в порядке запроса; результат — в том же порядке
     */
    public static List<PeriodDefectCounts> countByPeriods(Collection<KaitenCard> uniqueCards,
                                                          List<Period> periods) {
        List<PeriodDefectCounts> result = new ArrayList<>(periods.size());
        for (Period period : periods) {
            result.add(countInPeriod(uniqueCards, period));
        }
        return result;
    }

    private static PeriodDefectCounts countInPeriod(Collection<KaitenCard> cards, Period period) {
        int crit = 0;
        int high = 0;
        int med = 0;
        int low = 0;
        int unk = 0;
        int aiAgent = 0;
        for (KaitenCard card : cards) {
            if (card.cardType() != KaitenCardType.DEFECT || !createdInPeriod(card, period)) {
                continue;
            }
            switch (card.urgency()) {
                case CRITICAL -> crit++;
                case HIGH -> high++;
                case MEDIUM -> med++;
                case LOW -> low++;
                default -> unk++;
            }
            if (card.aiAgent()) {
                aiAgent++;
            }
        }
        return new PeriodDefectCounts(period, new UrgencyCounts(crit, high, med, low, unk), aiAgent);
    }

    private static boolean createdInPeriod(KaitenCard card, Period period) {
        LocalDateTime createdAt = card.createdAt();
        if (createdAt == null) {
            return false;
        }
        return !createdAt.isBefore(period.fromAtStartOfDay())
                && !createdAt.isAfter(period.toAtEndOfDay());
    }

    /**
     * Уникальные карточки-дефекты, {@code createdAt} которых попадает хотя бы в один период —
     * для детальной таблицы. Порядок: по убыванию {@code createdAt} (свежие сверху); входной
     * коллекции уже без дубликатов (дедуп по id сделал вызывающий).
     */
    public static List<KaitenCard> uniqueDefectsInAnyPeriod(Collection<KaitenCard> uniqueCards,
                                                            List<Period> periods) {
        List<KaitenCard> result = new ArrayList<>();
        for (KaitenCard card : uniqueCards) {
            if (card.cardType() != KaitenCardType.DEFECT) {
                continue;
            }
            boolean inAny = periods.stream().anyMatch(p -> createdInPeriod(card, p));
            if (inAny) {
                result.add(card);
            }
        }
        result.sort(Comparator.comparing(KaitenCard::createdAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }
}
