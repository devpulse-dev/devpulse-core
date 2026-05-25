package ru.x5.markable.dev.analytics.adapter.rest;

import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.x5.markable.dev.analytics.adapter.rest.dto.CommitResponse;
import ru.x5.markable.dev.analytics.adapter.rest.dto.UserProfileResponse;
import ru.x5.markable.dev.analytics.application.port.in.GetUserCommitsUseCase;
import ru.x5.markable.dev.analytics.application.port.in.GetUserProfileUseCase;
import ru.x5.markable.dev.analytics.domain.common.PageRequest;
import ru.x5.markable.dev.analytics.domain.common.Period;
import ru.x5.markable.dev.analytics.domain.model.user.Email;

/**
 * Эндпоинты пользователя: профиль с агрегированной статистикой и список коммитов с пагинацией.
 */
@RestController
@RequestMapping("/api/v2/users")
@RequiredArgsConstructor
public class UsersController {

    private final GetUserProfileUseCase getUserProfile;
    private final GetUserCommitsUseCase getUserCommits;

    @GetMapping("/{email}/profile")
    public ResponseEntity<UserProfileResponse> profile(
            @PathVariable String email,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        // Дефолт: последние 30 дней — основной кейс «открыть профиль с главного борда».
        Period period = DashboardController.resolvePeriod(from, to);
        return getUserProfile.findProfile(new Email(email), period)
                .map(UserProfileResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{email}/commits")
    public ResponseEntity<List<CommitResponse>> commits(
            @PathVariable String email,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        var data = getUserCommits
                .find(new Email(email), new Period(from, to), new PageRequest(page, size))
                .stream().map(CommitResponse::from).toList();
        return ResponseEntity.ok(data);
    }
}
