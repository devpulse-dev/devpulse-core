package ru.x5.devpulse.domain.model.performance;

import ru.x5.devpulse.domain.model.kaiten.KaitenUrgency;

/**
 * Закрытый в периоде критичный/высокий дефект — пруф «тушения пожаров».
 */
public record FirefightingItem(long id, String title, String url, KaitenUrgency urgency) {}
