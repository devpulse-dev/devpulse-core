package ru.x5.markable.dev.analytics.kaiten.service;

import ru.x5.markable.dev.analytics.kaiten.rest.dto.KaitenSpaceDto;

import java.util.List;

public interface KaitenSpaceService {
    List<KaitenSpaceDto> getAllSpaces();
}
