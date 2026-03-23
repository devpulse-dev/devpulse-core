package ru.x5.markable.dev.analytics.gitlab.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import ru.x5.markable.dev.analytics.gitlab.config.AiProperties;
import ru.x5.markable.dev.analytics.gitlab.rest.dto.UserProfileDto;

import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

@Component
@Log4j2
@RequiredArgsConstructor
public class AiClient {

    private final AiProperties aiProperties;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String generate(UserProfileDto profile) {
        log.info("Calling corporate AI for user: {}", profile.getEmail());
        log.info("AI URL: {}", aiProperties.getUrl());

        String prompt = buildPrompt(profile);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            if (aiProperties.getApiKey() != null && !aiProperties.getApiKey().isEmpty()) {
                headers.set("Authorization", "Bearer " + aiProperties.getApiKey());
            }

            // OpenAI-compatible request body
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", aiProperties.getModel());
            requestBody.put("messages", List.of(
                    Map.of("role", "user", "content", prompt)
            ));
            requestBody.put("temperature", 0.6);
            requestBody.put("max_tokens", 2000);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            // Правильный URL для OpenAI-совместимого API
            String url = aiProperties.getUrl();
            if (!url.endsWith("/chat/completions")) {
                url = url + "/chat/completions";
            }

            log.info("Request URL: {}", url);
            log.debug("Request body: {}", objectMapper.writeValueAsString(requestBody));

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            log.info("Response status: {}", response.getStatusCode());
            log.debug("Response body: {}", response.getBody());

            return parseOpenAiResponse(response.getBody());

        } catch (Exception e) {
            log.error("Error calling corporate AI", e);
            return getFallbackSummary(profile);
        }
    }

    private String parseOpenAiResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            // Стандартный OpenAI формат
            if (root.has("choices") && root.get("choices").isArray() && root.get("choices").size() > 0) {
                JsonNode firstChoice = root.get("choices").get(0);
                if (firstChoice.has("message") && firstChoice.get("message").has("content")) {
                    return firstChoice.get("message").get("content").asText();
                }
                if (firstChoice.has("text")) {
                    return firstChoice.get("text").asText();
                }
            }

            // Если формат другой
            log.warn("Unexpected response format: {}", responseBody);
            return responseBody;

        } catch (Exception e) {
            log.error("Error parsing AI response", e);
            return "Не удалось распарсить ответ AI";
        }
    }

    private String buildPrompt(UserProfileDto profile) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");

        // Активные часы (топ 3)
        String peakHours = profile.getActivityByHour().entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(3)
                .map(e -> e.getKey() + ":00")
                .collect(Collectors.joining(", "));

        // Активные дни (топ 3)
        String activeDays = profile.getActivityByDay().entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(3)
                .map(e -> e.getKey() + " (" + e.getValue() + " коммитов)")
                .collect(Collectors.joining(", "));

        // Топ задач
        String topTasks = profile.getTasks().stream()
                .limit(5)
                .map(t -> String.format("  - %s (%d коммитов)", t.getTaskNumber(), t.getCommits().size()))
                .collect(Collectors.joining("\n"));

        // Тип активности
        String activityType = getActivityType(profile.getActivityByHour());

        return String.format("""
            Ты аналитик DevOps. Проанализируй активность разработчика и составь краткий отчет на русском языке.
            
            ДАННЫЕ РАЗРАБОТЧИКА:
            - Имя: %s
            - Email: %s
            - Период: %s - %s
            - Всего коммитов: %d
            - Merge коммитов: %d
            - Добавлено строк: +%d
            - Удалено строк: -%d
            - Тестов добавлено: %d
            - Активных дней: %d из %d
            - Продуктивность: %.1f коммитов/день
            
            ПАТТЕРНЫ АКТИВНОСТИ:
            - Пик активности: %s
            - Активные часы: %s
            - Тип активности: %s
            
            ЗАДАЧИ (топ 5):
            %s
            
            ТРЕБОВАНИЯ К ОТВЕТУ:
            1. Напиши отчет на русском языке
            2. 3-5 предложений
            3. Выдели сильные стороны разработчика
            4. Дай 1-2 рекомендации
            
            ОТВЕТ:
            """,
                profile.getUsername(),
                profile.getEmail(),
                profile.getPeriodStart().format(formatter),
                profile.getPeriodEnd().format(formatter),
                profile.getTotalCommits(),
                profile.getTotalMergeCommits(),
                profile.getTotalAddedLines(),
                profile.getTotalDeletedLines(),
                profile.getTotalTestAddedLines(),
                profile.getActiveDays(),
                profile.getTotalDays(),
                (double) profile.getTotalCommits() / profile.getActiveDays(),
                activeDays.isEmpty() ? "не определены" : activeDays,
                peakHours.isEmpty() ? "не определены" : peakHours,
                activityType,
                topTasks.isEmpty() ? "  - нет задач" : topTasks
        );
    }

    private String getActivityType(Map<Integer, Long> activityByHour) {
        long morning = 0, day = 0, evening = 0, night = 0;

        for (Map.Entry<Integer, Long> e : activityByHour.entrySet()) {
            int hour = e.getKey();
            long count = e.getValue();
            if (hour >= 6 && hour <= 11) morning += count;
            else if (hour >= 12 && hour <= 17) day += count;
            else if (hour >= 18 && hour <= 23) evening += count;
            else night += count;
        }

        Map<String, Long> map = new LinkedHashMap<>();
        map.put("утренняя (6-11)", morning);
        map.put("дневная (12-17)", day);
        map.put("вечерняя (18-23)", evening);
        map.put("ночная (0-5)", night);

        return map.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("не определена");
    }

    public boolean isAvailable() {
        try {
            String url = aiProperties.getUrl();
            if (!url.endsWith("/chat/completions")) {
                url = url + "/chat/completions";
            }

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + aiProperties.getApiKey());
            HttpEntity<Void> request = new HttpEntity<>(headers);

            // Просто проверяем, что сервер отвечает
            restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    String.class
            );
            return true;
        } catch (Exception e) {
            log.warn("Corporate AI is not available: {}", e.getMessage());
            return false;
        }
    }

    private String getFallbackSummary(UserProfileDto profile) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");

        String peakDays = profile.getActivityByDay().entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(3)
                .map(e -> e.getKey() + " (" + e.getValue() + ")")
                .collect(Collectors.joining(", "));

        String peakHours = profile.getActivityByHour().entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(3)
                .map(e -> e.getKey() + ":00")
                .collect(Collectors.joining(", "));

        String topTasks = profile.getTasks().stream()
                .limit(5)
                .map(t -> String.format("  - %s (%d коммитов)", t.getTaskNumber(), t.getCommits().size()))
                .collect(Collectors.joining("\n"));

        return String.format("""
            📊 СВОДКА АКТИВНОСТИ (базовая статистика)
            
            Разработчик: %s
            Период: %s - %s
            
            📈 ОСНОВНЫЕ ПОКАЗАТЕЛИ:
            - Коммитов: %d
            - Добавлено строк: +%d
            - Удалено строк: -%d
            - Активных дней: %d из %d
            
            📅 ПИКИ АКТИВНОСТИ:
            - Дни: %s
            - Часы: %s
            
            🎯 ЗАДАЧИ:
            %s
            
            ⚠️ AI сервис временно недоступен. Отображаются базовые метрики.
            """,
                profile.getUsername(),
                profile.getPeriodStart().format(formatter),
                profile.getPeriodEnd().format(formatter),
                profile.getTotalCommits(),
                profile.getTotalAddedLines(),
                profile.getTotalDeletedLines(),
                profile.getActiveDays(),
                profile.getTotalDays(),
                peakDays,
                peakHours.isEmpty() ? "не определены" : peakHours,
                topTasks.isEmpty() ? "  - нет задач" : topTasks
        );
    }
}
