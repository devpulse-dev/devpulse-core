package ru.x5.markable.dev.analytics.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.Architectures;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Защитные тесты гексагональной архитектуры.
 *
 * <p>Падают как обычные unit-тесты, если кто-то нечаянно нарушил направление зависимостей.
 * Запускаются в bootstrap-модуле, потому что только он видит все остальные модули на classpath.</p>
 */
class HexagonalArchitectureTest {

    private static final String ROOT = "ru.x5.markable.dev.analytics";

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
            .importPackages(ROOT);

    @Test
    void layeredHexagon() {
        // bootstrap-модуль содержит только Application.java в корневом пакете ROOT.
        // Адаптеры наружу никем не должны вызываться (только сам Spring-runtime).
        // withOptionalLayers(true) — пока модули пустые, не считать это ошибкой.
        ArchRule rule = Architectures.layeredArchitecture()
                .consideringAllDependencies()
                .withOptionalLayers(true)
                .layer("domain").definedBy(ROOT + ".domain..")
                .layer("application").definedBy(ROOT + ".application..")
                .layer("adapter-rest").definedBy(ROOT + ".adapter.rest..")
                .layer("adapter-persistence").definedBy(ROOT + ".adapter.persistence..")
                .layer("adapter-git").definedBy(ROOT + ".adapter.git..")
                .layer("adapter-kaiten").definedBy(ROOT + ".adapter.kaiten..")

                .whereLayer("adapter-rest").mayNotBeAccessedByAnyLayer()
                .whereLayer("adapter-persistence").mayNotBeAccessedByAnyLayer()
                .whereLayer("adapter-git").mayNotBeAccessedByAnyLayer()
                .whereLayer("adapter-kaiten").mayNotBeAccessedByAnyLayer()
                .whereLayer("application")
                        .mayOnlyBeAccessedByLayers(
                                "adapter-rest", "adapter-persistence",
                                "adapter-git", "adapter-kaiten")
                .whereLayer("domain")
                        .mayOnlyBeAccessedByLayers(
                                "application", "adapter-rest", "adapter-persistence",
                                "adapter-git", "adapter-kaiten");

        rule.check(CLASSES);
    }

    @Test
    void domainHasNoFrameworkDependencies() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(ROOT + ".domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "jakarta.servlet..",
                        "com.fasterxml.jackson..",
                        "org.hibernate..",
                        "lombok.."
                )
                .allowEmptyShould(true);

        rule.check(CLASSES);
    }

    @Test
    void applicationHasNoFrameworkDependencies() {
        // Application может использовать Lombok для краткости, но НЕ Spring/JPA/HTTP.
        ArchRule rule = noClasses()
                .that().resideInAPackage(ROOT + ".application..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "jakarta.servlet..",
                        "com.fasterxml.jackson..",
                        "org.hibernate.."
                )
                .allowEmptyShould(true);

        rule.check(CLASSES);
    }
}
