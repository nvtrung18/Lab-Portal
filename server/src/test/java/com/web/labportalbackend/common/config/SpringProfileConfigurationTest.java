package com.web.labportalbackend.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

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

    private Properties load(String resource) {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new FileSystemResource("src/main/resources/" + resource));
        Properties properties = factory.getObject();
        assertThat(properties).isNotNull();
        return properties;
    }
}
