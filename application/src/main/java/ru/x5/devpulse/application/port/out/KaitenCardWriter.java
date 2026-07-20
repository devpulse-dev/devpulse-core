package ru.x5.devpulse.application.port.out;

import ru.x5.devpulse.domain.model.kaiten.KaitenCardId;

/**
 * Port out: запись карточек Kaiten. Отделён от read-порта {@link KaitenGateway} — сбор/чтение
 * и мутации карточек имеют разные права и жизненный цикл.
 */
public interface KaitenCardWriter {

    /**
     * Проставить карточке флаг «AI-Agent» (Kaiten property {@code id_6064 = true}).
     * Идемпотентно (set-only). Кидает RuntimeException при ошибке Kaiten — вызывающий
     * решает, ронять ли всю пачку или продолжать.
     */
    void markAiAgent(KaitenCardId cardId);
}
