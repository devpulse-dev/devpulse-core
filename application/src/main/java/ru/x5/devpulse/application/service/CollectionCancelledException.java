package ru.x5.devpulse.application.service;

/**
 * Внутренний control-flow сигнал: фаза сбора обнаружила запрос отмены на checkpoint'е и
 * прекращает работу. Ловится оркестратором ({@code CollectDailyStatsService}) → прогон
 * помечается {@code CANCELLED}. Не покидает application-слой.
 */
final class CollectionCancelledException extends RuntimeException {

    CollectionCancelledException(String message) {
        super(message);
    }
}
