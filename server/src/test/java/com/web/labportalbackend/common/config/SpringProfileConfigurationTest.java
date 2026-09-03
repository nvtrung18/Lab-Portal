package com.web.labportalbackend.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpringProfileConfigurationTest {

    @Test
    void productionProfileHasNoLocalhostOrSecretFallbacks() {
        Properties properties = load("application-prod.yml");

        assertThat(properties.getProperty("spring.datasource.url")).contains("${DB_HOST}", "${DB_PORT}", "${DB_NAME}").doesNotContain("localhost");
        assertThat(properties.getProperty("spring.datasource.username")).isEqualTo("${DB_USERNAME}");
        assertThat(properties.getProperty("spring.datasource.password")).isEqualTo("${DB_PASSWORD}");
        assertThat(properties.getProperty("spring.data.redis.host")).isEqualTo("${REDIS_HOST}");
        assertThat(properties.getProperty("jwt.secret")).isEqualTo("${JWT_SECRET}");
        assertThat(properties.getProperty("app.dev-seed.enabled")).isEqualTo("false");
        assertThat(properties.getProperty("logging.level.org.hibernate.SQL")).isEqualTo("WARN");
    }

    @Test
    void baseProfileUsesRuntimePortAndSharedContextPath() {
        Properties properties = load("application.yml");

        assertThat(properties.getProperty("server.port")).isEqualTo("${PORT:8080}");
        assertThat(properties.getProperty("server.servlet.context-path")).isEqualTo("/api");
        assertThat(properties.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health");
    }

    @Test
    void performanceProfileOptsIntoBoundedHttpAndHibernateMetrics() {
        Properties properties = load("application-performance.yml");

        assertThat(properties.getProperty("spring.config.activate.on-profile")).isEqualTo("performance");
        assertThat(properties.getProperty("spring.jpa.properties.hibernate.generate_statistics")).isEqualTo("true");
        assertThat(properties.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health,metrics");
        assertThat(properties.getProperty(
                "management.metrics.distribution.percentiles-histogram.http.server.requests")).isEqualTo("true");
        assertThat(properties.getProperty(
                "management.metrics.distribution.percentiles.http.server.requests")).isEqualTo("0.5,0.95,0.99");
    }

    @Test
    void productionProfileFailsClearlyWhenRequiredDatabaseHostIsMissing() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addLast(
                new PropertiesPropertySource("application-prod.yml", load("application-prod.yml"))
        );

        assertThatThrownBy(() -> environment.getProperty("spring.datasource.url"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DB_HOST");
    }

    private Properties load(String resource) {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new FileSystemResource("src/main/resources/" + resource));
        Properties properties = factory.getObject();
        assertThat(properties).isNotNull();
        return properties;
    }
}
