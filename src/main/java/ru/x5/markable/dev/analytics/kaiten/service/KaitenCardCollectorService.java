package ru.x5.markable.dev.analytics.kaiten.service;

import java.time.LocalDateTime;
import java.util.List;

public interface KaitenCardCollectorService {
    void collectCardsFromAllSpaces(LocalDateTime since);
    void collectCardsForTeam(List<String> teamEmails, LocalDateTime since);

}
