package ru.x5.markable.dev.analytics.kaiten.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import ru.x5.markable.dev.analytics.kaiten.client.KaitenClient;
import ru.x5.markable.dev.analytics.kaiten.config.KaitenProperties;
import ru.x5.markable.dev.analytics.kaiten.rest.dto.KaitenSpaceDto;
import ru.x5.markable.dev.analytics.kaiten.service.KaitenSpaceService;

import java.util.List;

/**
 * Сервис для управления пространствами Kaiten.
 * 
 * <p>Обеспечивает получение пространств из системы Kaiten с возможностью
 * фильтрации по белому списку ID пространств.</p>
 * 
 * <p>Основные функции:</p>
 * <ul>
 *   <li>Получение всех пространств из Kaiten</li>
 *   <li>Фильтрация пространств по белому списку</li>
 * </ul>
 * 
 * @author Markable Development Team
 * @version 1.0
 * @see KaitenSpaceService
 * @see KaitenSpaceDto
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class KaitenSpaceServiceImpl implements KaitenSpaceService {

    private final KaitenClient kaitenClient;
    private final KaitenProperties properties;

    /**
     * Получает все пространства из Kaiten.
     * 
     * <p>Если в конфигурации задан белый список пространств (spaceIds),
     * возвращает только пространства из этого списка. Иначе возвращает все пространства.</p>
     * 
     * @return список пространств (отфильтрованный или все)
     */
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
