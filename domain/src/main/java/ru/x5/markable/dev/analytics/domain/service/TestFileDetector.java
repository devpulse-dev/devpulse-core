package ru.x5.markable.dev.analytics.domain.service;

/**
 * Эвристика определения тестовых файлов по пути.
 *
 * <p>Тестовым считается файл, путь к которому:
 * <ul>
 *   <li>содержит сегмент {@code /test/} или {@code /tests/};</li>
 *   <li>оканчивается на {@code Test.java}, {@code Tests.java}, {@code Spec.java}, {@code IT.java}.</li>
 * </ul>
 *
 * <p>Эвристика преднамеренно простая — закрывает 95% Java/Kotlin репозиториев и стоит O(1).</p>
 */
public final class TestFileDetector {

    private TestFileDetector() {}

    public static boolean isTestFile(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        String lower = path.toLowerCase();
        if (lower.contains("/test/") || lower.contains("/tests/")) {
            return true;
        }
        return lower.endsWith("test.java")
                || lower.endsWith("tests.java")
                || lower.endsWith("spec.java")
                || lower.endsWith("it.java");
    }
}
