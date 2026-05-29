package ru.x5.devpulse.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import ru.x5.devpulse.application.port.in.GetUserCommitsUseCase;
import ru.x5.devpulse.application.port.out.CommitRepository;
import ru.x5.devpulse.domain.common.PageRequest;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.git.Commit;
import ru.x5.devpulse.domain.model.user.Email;

/** Коммиты пользователя за период с пагинацией. */
@RequiredArgsConstructor
public final class GetUserCommitsService implements GetUserCommitsUseCase {

    private final CommitRepository commitRepository;

    @Override
    public List<Commit> find(Email email, Period period, PageRequest page) {
        return commitRepository.findByAuthor(email, period, page);
    }
}
