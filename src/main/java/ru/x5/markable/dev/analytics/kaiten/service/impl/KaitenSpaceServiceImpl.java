package ru.x5.markable.dev.analytics.kaiten.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import ru.x5.markable.dev.analytics.kaiten.client.KaitenClient;
import ru.x5.markable.dev.analytics.kaiten.config.KaitenProperties;
import ru.x5.markable.dev.analytics.kaiten.rest.dto.KaitenSpaceDto;
import ru.x5.markable.dev.analytics.kaiten.service.KaitenSpaceService;

import java.util.List;

@Service
@Log4j2
@RequiredArgsConstructor
public class KaitenSpaceServiceImpl implements KaitenSpaceService {

    private final KaitenClient kaitenClient;
    private final KaitenProperties properties;

    @Override
    public List<KaitenSpaceDto> getAllSpaces() {
        List<KaitenSpaceDto> allSpaces = kaitenClient.getSpaces();

        // Если задан белый список пространств - фильтруем
        if (properties.getSpaceIds() != null && !properties.getSpaceIds().isEmpty()) {
            List<KaitenSpaceDto> filteredSpaces = allSpaces.stream()
                    .filter(space -> properties.getSpaceIds().contains(space.getId()))
                    .toList();
            log.info("Filtered spaces: {} of {} (allowed ids: {})",
                    filteredSpaces.size(), allSpaces.size(), properties.getSpaceIds());
            return filteredSpaces;
        }

        log.info("All spaces: {}", allSpaces.size());
        return allSpaces;
    }
}
