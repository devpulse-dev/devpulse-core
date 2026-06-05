package ru.x5.devpulse.domain.model.performance;

/**
 * Заметный артефакт (карточка Kaiten / merge request) со ссылкой — «пруф» к разговору
 * на perf-review.
 */
public record PerformanceHighlight(Kind kind, String title, String subtitle, String url) {

    public enum Kind { CARD, MR }
}
