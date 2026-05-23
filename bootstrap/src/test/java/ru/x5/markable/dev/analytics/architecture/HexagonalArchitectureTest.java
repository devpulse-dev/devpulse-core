package ru.x5.markable.dev.analytics.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.Architectures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Защитные тесты гексагональной архитектуры.
 *
 * <p>Падают как обычные unit-тесты, если кто-то нечаянно нарушил направление зависимостей.
 * Запускаются в bootstrap-модуле, потому что только он видит все остальные модули на classpath.</p>
 */
@DisplayName("Архитектурные правила: гексагональная архитектура")
class HexagonalArchitectureTest {

    private static final String ROOT = "ru.x5.markable.dev.analytics";

    private static final String DOMAIN = ROOT + ".domain..";
    private static final String APPLICATION = ROOT + ".application..";
    private static final String ADAPTER_REST = ROOT + ".adapter.rest..";
    private static final String ADAPTER_PERSISTENCE = ROOT + ".adapter.persistence..";
    private static final String ADAPTER_GIT = ROOT + ".adapter.git..";
    private static final String ADAPTER_KAITEN = ROOT + ".adapter.kaiten..";

    /**
     * Composition root: {@code @SpringBootApplication} в корневом пакете + {@code .config..}
     * с {@code @Configuration}-классами, которые wire-ят use case-ы из портов.
     * Bootstrap по построению видит всех — это правильно. Никто не должен зависеть ОТ bootstrap.
     */
    private static final String[] BOOTSTRAP = {
            ROOT,
            ROOT + ".config..",
    };

    private static final String[] FORBIDDEN_FOR_DOMAIN = {
            "org.springframework..",
            "jakarta.persistence..",
            "jakarta.servlet..",
            "com.fasterxml.jackson..",
            "org.hibernate..",
            "lombok.."
    };

    private static final String[] FORBIDDEN_FOR_APPLICATION = {
            "org.springframework..",
            "jakarta.persistence..",
            "jakarta.servlet..",
            "com.fasterxml.jackson..",
            "org.hibernate.."
    };

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
            .importPackages(ROOT);

    @Test
    @DisplayName("Направление зависимостей: адаптеры → application → domain (никогда наоборот)")
    void layeredHexagon() {
        ArchRule rule = Architectures.layeredArchitecture()
                .consideringAllDependencies()
                .withOptionalLayers(true)
                .layer("domain").definedBy(DOMAIN)
                .layer("application").definedBy(APPLICATION)
                .layer("adapter-rest").definedBy(ADAPTER_REST)
                .layer("adapter-persistence").definedBy(ADAPTER_PERSISTENCE)
                .layer("adapter-git").definedBy(ADAPTER_GIT)
                .layer("adapter-kaiten").definedBy(ADAPTER_KAITEN)
                .layer("bootstrap").definedBy(BOOTSTRAP)

                .whereLayer("bootstrap").mayNotBeAccessedByAnyLayer()
                .whereLayer("adapter-rest").mayOnlyBeAccessedByLayers("bootstrap")
                .whereLayer("adapter-persistence").mayOnlyBeAccessedByLayers("bootstrap")
                .whereLayer("adapter-git").mayOnlyBeAccessedByLayers("bootstrap")
                .whereLayer("adapter-kaiten").mayOnlyBeAccessedByLayers("bootstrap")
                .whereLayer("application")
                        .mayOnlyBeAccessedByLayers(
                                "bootstrap",
                                "adapter-rest", "adapter-persistence",
                                "adapter-git", "adapter-kaiten")
                .whereLayer("domain")
                        .mayOnlyBeAccessedByLayers(
                                "bootstrap", "application",
                                "adapter-rest", "adapter-persistence",
                                "adapter-git", "adapter-kaiten");

        rule.check(CLASSES);
    }

    @Test
    @DisplayName("Domain: никаких зависимостей от Spring/JPA/Jackson/Hibernate/Lombok")
    void domainHasNoFrameworkDependencies() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(DOMAIN)
                .should().dependOnClassesThat()
                .resideInAnyPackage(FORBIDDEN_FOR_DOMAIN)
                .allowEmptyShould(true);

        rule.check(CLASSES);
    }

    @Test
    @DisplayName("Application: никаких зависимостей от Spring/JPA/Jackson/Hibernate (Lombok можно)")
    void applicationHasNoFrameworkDependencies() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(APPLICATION)
                .should().dependOnClassesThat()
                .resideInAnyPackage(FORBIDDEN_FOR_APPLICATION)
                .allowEmptyShould(true);

        rule.check(CLASSES);
    }
}
