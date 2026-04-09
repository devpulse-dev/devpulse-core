package ru.x5.markable.dev.analytics.kaiten.rest.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class KaitenSyncRequest {
    private List<String> teamEmails;
    private LocalDateTime since;
}
