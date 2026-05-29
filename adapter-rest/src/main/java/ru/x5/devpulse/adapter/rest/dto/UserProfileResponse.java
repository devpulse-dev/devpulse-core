package ru.x5.devpulse.adapter.rest.dto;

import java.util.List;
import ru.x5.devpulse.application.port.in.GetUserProfileUseCase;

/** Профиль пользователя для REST-ответа. */
public record UserProfileResponse(
        UnifiedUserResponse user,
        AuthorSummaryResponse summary,
        List<CommitResponse> commits,
        List<KaitenCardResponse> cards
) {
    public static UserProfileResponse from(GetUserProfileUseCase.Profile p) {
        return new UserProfileResponse(
                UnifiedUserResponse.from(p.user()),
                AuthorSummaryResponse.from(p.summary()),
                p.commits().stream().map(CommitResponse::from).toList(),
                p.cards().stream().map(KaitenCardResponse::from).toList());
    }
}
