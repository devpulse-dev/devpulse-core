package ru.x5.markable.dev.analytics.gitlab.service;

import java.time.LocalDate;
import java.util.List;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.CommitDetailDto;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.UserProfileDto;

public interface UserProfileService {

    /**
     * Получить профиль пользователя за весь период
     */
    UserProfileDto getUserProfile(String email);

    /**
     * Получить профиль пользователя за указанный период
     */
    UserProfileDto getUserProfile(String email, LocalDate periodStart, LocalDate periodEnd);

    List<CommitDetailDto> getUserCommits(String email);
}
