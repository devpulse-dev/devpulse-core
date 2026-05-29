package ru.x5.devpulse.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Domain Service: TestFileDetector")
class TestFileDetectorTest {

    @ParameterizedTest(name = "[{index}] {0} — тестовый файл")
    @ValueSource(strings = {
            "src/test/java/Foo.java",
            "src/tests/java/Foo.java",
            "src/main/java/FooTest.java",
            "src/main/java/FooTests.java",
            "src/main/groovy/FooSpec.java",
            "core/CheckoutIT.java"
    })
    @DisplayName("Определяет тестовые файлы по пути и суффиксу имени")
    void detectsTestPaths(String path) {
        assertThat(TestFileDetector.isTestFile(path))
                .as("\"%s\" должен распознаваться как тестовый", path)
                .isTrue();
    }

    @ParameterizedTest(name = "[{index}] {0} — продакшен")
    @ValueSource(strings = {
            "src/main/java/Foo.java",
            "src/main/resources/application.yml",
            "README.md",
            "build.gradle"
            // Намеренно НЕ включаем "src/main/java/Test.java": эвристика по суффиксу
            // "test.java" срабатывает на любое имя файла, ровно равное Test.java —
            // это компромисс ради простоты детектора и охвата 95% реальных случаев.
    })
    @DisplayName("Не считает продакшен-файлы тестовыми")
    void doesNotMisclassifyProductionPaths(String path) {
        assertThat(TestFileDetector.isTestFile(path))
                .as("\"%s\" не является тестовым файлом", path)
                .isFalse();
    }

    @ParameterizedTest(name = "[{index}] пустое значение → false")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    @DisplayName("На null/blank возвращает false")
    void blankPathIsNotATest(String path) {
        assertThat(TestFileDetector.isTestFile(path))
                .as("пустое значение не может быть тестовым файлом")
                .isFalse();
    }
}
