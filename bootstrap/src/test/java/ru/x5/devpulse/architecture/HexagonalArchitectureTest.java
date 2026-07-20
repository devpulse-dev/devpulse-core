package ru.x5.devpulse.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static org.assertj.core.api.Assertions.assertThat;

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
 * <p>Падают как обычные unit-тесты, если кто-то нечаянно нарушил архитектурное правило.
 * Запускаются в bootstrap-модуле, потому что только он видит все остальные модули на classpath.</p>
 *
 * <p>Правила в этом классе ловят <b>реальные</b> регрессии: тип, видимость, framework-зависимости,
 * запрещённые API. Не пишите правил "ради красоты" — каждое должно описывать конкретный риск.</p>
 */
@DisplayName("Архитектурные правила: гексагональная архитектура")
class HexagonalArchitectureTest {

    private static final String ROOT = "ru.x5.devpulse";

    private static final String DOMAIN = ROOT + ".domain..";
    private static final String DOMAIN_MODEL = ROOT + ".domain.model..";
    private static final String DOMAIN_SERVICE = ROOT + ".domain.service..";
    private static final String APPLICATION = ROOT + ".application..";
    private static final String APPLICATION_PORT_IN = ROOT + ".application.port.in..";
    private static final String APPLICATION_PORT_OUT = ROOT + ".application.port.out..";
    private static final String APPLICATION_SERVICE = ROOT + ".application.service..";
    private static final String ADAPTER_REST = ROOT + ".adapter.rest..";
    private static final String ADAPTER_PERSISTENCE = ROOT + ".adapter.persistence..";
    private static final String ADAPTER_GIT = ROOT + ".adapter.git..";
    /** Shared GitLab transport (HTTP-клиент + DTO + rate-limiter): переиспользуется reviews и identity. */
    private static final String ADAPTER_GITLAB = ROOT + ".adapter.gitlab..";
    private static final String ADAPTER_KAITEN = ROOT + ".adapter.kaiten..";
    private static final String ADAPTER_REVIEWS = ROOT + ".adapter.reviews..";
    private static final String ADAPTER_IDENTITY = ROOT + ".adapter.identity..";
    private static final String ADAPTER_AUTH = ROOT + ".adapter.auth..";

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

    // БЕЗ DO_NOT_INCLUDE_JARS намеренно: при `mvn verify` (или `-pl bootstrap` после install) модули
    // резолвятся в JAR из ~/.m2, и DO_NOT_INCLUDE_JARS отбросил бы их все кроме bootstrap → ArchUnit
    // импортировал бы ~8 классов, и ВСЕ layered-правила прошли бы вакуумно (allowEmptyShould). Наш
    // importPackages(ROOT) и так фильтрует по пакету ru.x5.devpulse — сторонние JAR (spring и пр.)
    // в выборку не попадают. classpathIsNotVacuous страхует от регресса (см. ниже).
    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT);

    /* ====================== направление зависимостей ====================== */

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
                .layer("adapter-gitlab").definedBy(ADAPTER_GITLAB)
                .layer("adapter-kaiten").definedBy(ADAPTER_KAITEN)
                .layer("adapter-reviews").definedBy(ADAPTER_REVIEWS)
                .layer("adapter-identity").definedBy(ADAPTER_IDENTITY)
                .layer("adapter-auth").definedBy(ADAPTER_AUTH)
                .layer("bootstrap").definedBy(BOOTSTRAP)

                .whereLayer("bootstrap").mayNotBeAccessedByAnyLayer()
                .whereLayer("adapter-rest").mayOnlyBeAccessedByLayers("bootstrap")
                .whereLayer("adapter-persistence").mayOnlyBeAccessedByLayers("bootstrap")
                .whereLayer("adapter-git").mayOnlyBeAccessedByLayers("bootstrap")
                // Shared GitLab transport: доступен реализациям портов поверх него (reviews/identity)
                // и bootstrap. gitlab сам НЕ обращается к application/domain (чистый transport) —
                // это гарантируется тем, что его нет в списках mayOnlyBeAccessedBy для application/domain.
                .whereLayer("adapter-gitlab")
                        .mayOnlyBeAccessedByLayers("adapter-reviews", "adapter-identity", "bootstrap")
                .whereLayer("adapter-kaiten").mayOnlyBeAccessedByLayers("bootstrap")
                .whereLayer("adapter-reviews").mayOnlyBeAccessedByLayers("bootstrap")
                .whereLayer("adapter-identity").mayOnlyBeAccessedByLayers("bootstrap")
                .whereLayer("adapter-auth").mayOnlyBeAccessedByLayers("bootstrap")
                .whereLayer("application")
                        .mayOnlyBeAccessedByLayers(
                                "bootstrap",
                                "adapter-rest", "adapter-persistence",
                                "adapter-git", "adapter-kaiten", "adapter-reviews",
                                "adapter-identity", "adapter-auth")
                .whereLayer("domain")
                        .mayOnlyBeAccessedByLayers(
                                "bootstrap", "application",
                                "adapter-rest", "adapter-persistence",
                                "adapter-git", "adapter-kaiten", "adapter-reviews",
                                "adapter-identity", "adapter-auth");

        rule.check(CLASSES);
    }

    @Test
    @DisplayName("Sanity: импортированы классы всех модулей, а не только bootstrap (анти-вакуум)")
    void classpathIsNotVacuous() {
        // DO_NOT_INCLUDE_JARS + сплошной allowEmptyShould(true) на правилах = риск, что при сборке,
        // где модули резолвятся в JAR (mvn verify после install / -pl bootstrap), импортируются
        // только ~классы bootstrap, и ВСЕ правила проходят вакуумно-зелёными. Этот порог заведомо
        // ниже реального числа наших классов, но много выше «только bootstrap» → ловит вакуум.
        assertThat(CLASSES.size())
                .as("должны импортироваться классы всех модулей (domain/application/адаптеры), не только bootstrap")
                .isGreaterThan(100);
    }

    /* ====================== framework-bans ====================== */

    @Test
    @DisplayName("Domain: никаких зависимостей от Spring/JPA/Jackson/Hibernate/Lombok")
    void domainHasNoFrameworkDependencies() {
        noClasses()
                .that().resideInAPackage(DOMAIN)
                .should().dependOnClassesThat()
                .resideInAnyPackage(FORBIDDEN_FOR_DOMAIN)
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    @DisplayName("Application: никаких зависимостей от Spring/JPA/Jackson/Hibernate (Lombok можно)")
    void applicationHasNoFrameworkDependencies() {
        noClasses()
                .that().resideInAPackage(APPLICATION)
                .should().dependOnClassesThat()
                .resideInAnyPackage(FORBIDDEN_FOR_APPLICATION)
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    /* ====================== ports — контракт ====================== */

    @Test
    @DisplayName("port/in — только interfaces (use case определяет контракт, не реализацию)")
    void portsInAreInterfaces() {
        classes()
                .that().resideInAPackage(APPLICATION_PORT_IN)
                .should().beInterfaces()
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    @DisplayName("port/out — interfaces ИЛИ exceptions (исключения для типизации ошибок)")
    void portsOutAreInterfacesOrExceptions() {
        classes()
                .that().resideInAPackage(APPLICATION_PORT_OUT)
                .and().areNotNestedClasses()
                .should().beInterfaces()
                .orShould().beAssignableTo(RuntimeException.class)
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    @DisplayName("port/in НЕ зависит от port/out — use case'ы получают зависимости через DI, не зовут друг друга через порты")
    void portInDoesNotDependOnPortOut() {
        noClasses()
                .that().resideInAPackage(APPLICATION_PORT_IN)
                .should().dependOnClassesThat()
                .resideInAPackage(APPLICATION_PORT_OUT)
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    /* ====================== domain immutability ====================== */

    @Test
    @DisplayName("domain.model — record, enum или final class (без mutable inheritance)")
    void domainModelsAreImmutableShape() {
        classes()
                .that().resideInAPackage(DOMAIN_MODEL)
                .and().areNotNestedClasses()
                .and().areTopLevelClasses()
                .should().beRecords()
                .orShould().beEnums()
                .orShould().haveModifier(com.tngtech.archunit.core.domain.JavaModifier.FINAL)
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    @DisplayName("domain.service — final (pure-функции, наследовать нечего)")
    void domainServicesAreFinal() {
        classes()
                .that().resideInAPackage(DOMAIN_SERVICE)
                .and().areTopLevelClasses()
                .and().areNotInterfaces()
                .should().haveModifier(com.tngtech.archunit.core.domain.JavaModifier.FINAL)
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    @DisplayName("application.service — final (use case implementations не должны наследоваться)")
    void applicationServicesAreFinal() {
        classes()
                .that().resideInAPackage(APPLICATION_SERVICE)
                .and().areTopLevelClasses()
                .and().areNotInterfaces()
                .should().haveModifier(com.tngtech.archunit.core.domain.JavaModifier.FINAL)
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    /* ====================== framework-аннотации в правильных местах ====================== */

    @Test
    @DisplayName("@Component/@Service/@Repository НЕ должны появляться в application — он POJO")
    void applicationHasNoSpringStereotypes() {
        noClasses()
                .that().resideInAPackage(APPLICATION)
                .should().beAnnotatedWith("org.springframework.stereotype.Component")
                .orShould().beAnnotatedWith("org.springframework.stereotype.Service")
                .orShould().beAnnotatedWith("org.springframework.stereotype.Repository")
                .orShould().beAnnotatedWith("org.springframework.stereotype.Controller")
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    @DisplayName("@RestController — только в adapter.rest или adapter.auth (auth-web вынесен отдельно)")
    void restControllersOnlyInRestAdapter() {
        classes()
                .that().areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                .should().resideInAnyPackage(ADAPTER_REST, ADAPTER_AUTH)
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    @DisplayName("@Entity (JPA) — только в adapter.persistence")
    void jpaEntitiesOnlyInPersistenceAdapter() {
        classes()
                .that().areAnnotatedWith("jakarta.persistence.Entity")
                .should().resideInAPackage(ADAPTER_PERSISTENCE)
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    /* ====================== запрещённые API ====================== */

    @Test
    @DisplayName("Никаких System.out / System.err — пользоваться SLF4J")
    void noSystemOutErr() {
        // System.out/err — это static final поля. Запрещаем доступ к полям, не вызовы методов.
        noClasses()
                .should().accessField(System.class, "out")
                .orShould().accessField(System.class, "err")
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    @DisplayName("java.util.Date / Calendar запрещены — только java.time")
    void noLegacyDateApi() {
        noClasses()
                .should().dependOnClassesThat()
                .haveFullyQualifiedName("java.util.Date")
                .orShould().dependOnClassesThat()
                .haveFullyQualifiedName("java.util.Calendar")
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    @DisplayName("Никаких public mutable static полей — shared mutable state — источник bugs")
    void noPublicMutableStaticFields() {
        noFields()
                .should().beStatic()
                .andShould().bePublic()
                .andShould().notHaveModifier(com.tngtech.archunit.core.domain.JavaModifier.FINAL)
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    /* ====================== mapstruct в правильных местах ====================== */

    @Test
    @DisplayName("MapStruct (@Mapper) — только в адаптерах (entity ↔ domain — adapter concern)")
    void mapStructMappersOnlyInAdapters() {
        classes()
                .that().areAnnotatedWith("org.mapstruct.Mapper")
                .should().resideInAnyPackage(
                        ADAPTER_PERSISTENCE,
                        ADAPTER_REST,
                        ADAPTER_GIT,
                        ADAPTER_KAITEN)
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    /* ====================== «used» ====================== */

    /**
     * Это правило здесь чтобы покрыть нерегрессионный edge: {@code fields()} использовался выше.
     * Sanity: проверка что доменные классы не имеют public mutable полей.
     */
    @Test
    @DisplayName("Domain классы не имеют public non-final полей")
    void domainHasNoPublicMutableFields() {
        fields()
                .that().areDeclaredInClassesThat().resideInAPackage(DOMAIN)
                .and().arePublic()
                .should().beFinal()
                .allowEmptyShould(true)
                .check(CLASSES);
    }
}
