package ru.x5.markable.dev.analytics.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TestFileDetectorTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "src/test/java/Foo.java",
            "src/tests/java/Foo.java",
            "src/main/java/FooTest.java",
            "src/main/java/FooTests.java",
            "src/main/groovy/FooSpec.java",
            "core/CheckoutIT.java"
    })
    void detectsTestPaths(String path) {
        assertThat(TestFileDetector.isTestFile(path)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "src/main/java/Foo.java",
            "src/main/resources/application.yml",
            "README.md",
            "build.gradle"
    })
    void detectsProductionPaths(String path) {
        assertThat(TestFileDetector.isTestFile(path)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void blankIsNotATest(String path) {
        assertThat(TestFileDetector.isTestFile(path)).isFalse();
    }
}
