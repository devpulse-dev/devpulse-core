package ru.x5.markable.dev.analytics.adapter.persistence;

import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.AbstractEntityManagerFactoryBean;

/**
 * Явная конфигурация Liquibase.
 *
 * <p>В Spring Boot 4 LiquibaseAutoConfiguration вынесен в отдельный модуль и при подключении только
 * {@code liquibase-core} автоконфигурация не активируется. Прописываем bean руками:</p>
 *
 * <ul>
 *   <li>{@link SpringLiquibase} запускает миграции в {@code afterPropertiesSet()};</li>
 *   <li>{@link AbstractEntityManagerFactoryBean} объявляется зависимым от bean {@code liquibase},
 *       чтобы Hibernate инициализировался уже после применения миграций.</li>
 * </ul>
 */
@Configuration
public class LiquibaseConfig {

    @Bean
    public SpringLiquibase liquibase(
            DataSource dataSource,
            @Value("${spring.liquibase.change-log:classpath:liquibase/changelog.master.yml}") String changeLog,
            @Value("${spring.liquibase.enabled:true}") boolean enabled
    ) {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog(changeLog);
        liquibase.setShouldRun(enabled);
        return liquibase;
    }

    /**
     * BeanFactoryPostProcessor: делает EntityManagerFactory зависимым от Liquibase,
     * чтобы Hibernate стартовал после применения миграций.
     */
    @Bean
    public static org.springframework.beans.factory.config.BeanFactoryPostProcessor
            entityManagerFactoryDependsOnLiquibase() {
        return beanFactory -> {
            if (!(beanFactory instanceof org.springframework.beans.factory.support.BeanDefinitionRegistry registry)) {
                return;
            }
            // getBeanNamesForType для FactoryBean возвращает имена с префиксом '&' (так Spring
            // отличает сам FactoryBean от его product); getBeanDefinition этот префикс не принимает.
            for (String name : beanFactory.getBeanNamesForType(AbstractEntityManagerFactoryBean.class, false, false)) {
                String defName = name.startsWith("&") ? name.substring(1) : name;
                var def = registry.getBeanDefinition(defName);
                String[] existing = def.getDependsOn();
                def.setDependsOn(existing == null
                        ? new String[]{"liquibase"}
                        : appendIfMissing(existing, "liquibase"));
            }
        };
    }

    private static String[] appendIfMissing(String[] arr, String value) {
        for (String s : arr) {
            if (value.equals(s)) return arr;
        }
        String[] result = new String[arr.length + 1];
        System.arraycopy(arr, 0, result, 0, arr.length);
        result[arr.length] = value;
        return result;
    }
}
