package ru.x5.devpulse.domain.model.performance;

import ru.x5.devpulse.domain.model.kaiten.KaitenCardType;
import ru.x5.devpulse.domain.model.kaiten.KaitenColumnStatus;

/** Карточка-юскейс внутри корневой задачи (лист аккордеона). */
public record UseCaseRef(
        long id,
        String title,
        String url,
        KaitenColumnStatus status,
        KaitenCardType type
) {}
