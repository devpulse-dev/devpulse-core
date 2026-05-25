package ru.x5.markable.dev.analytics.adapter.git;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Активирует {@link GitProperties} как Spring-бин при наличии adapter-git на classpath.
 */
@Configuration
@EnableConfigurationProperties(GitProperties.class)
class GitAdapterConfig {
}
