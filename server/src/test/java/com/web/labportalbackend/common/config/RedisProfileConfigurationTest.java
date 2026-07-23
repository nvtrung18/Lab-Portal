package com.web.labportalbackend.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.time.Duration;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class RedisProfileConfigurationTest {

    @Test
    void productionRedisSettingsAreExternalizedWithTlsAndFailFastTimeout() {
        Properties properties = load("application-prod.yml");

        assertThat(properties.getProperty("spring.data.redis.host")).isEqualTo("${REDIS_HOST}");
        assertThat(properties.getProperty("spring.data.redis.port")).isEqualTo("${REDIS_PORT}");
        assertThat(properties.getProperty("spring.data.redis.password")).isEqualTo("${REDIS_PASSWORD:}");
        assertThat(properties.getProperty("spring.data.redis.ssl.enabled")).isEqualTo("${REDIS_SSL_ENABLED}");
        assertThat(properties.getProperty("spring.data.redis.connect-timeout")).isEqualTo("${REDIS_CONNECT_TIMEOUT:2s}");
    }

    @Test
    void localRedisSettingsKeepLocalDefaultsWithoutChangingBusinessKeys() {
        Properties properties = load("application-local.yml");

        assertThat(properties.getProperty("spring.data.redis.host")).isEqualTo("${REDIS_HOST:localhost}");
        assertThat(properties.getProperty("spring.data.redis.port")).isEqualTo("${REDIS_PORT:6379}");
        assertThat(properties.getProperty("spring.data.redis.ssl.enabled")).isEqualTo("${REDIS_SSL_ENABLED:false}");
    }

    @Test
    void productionRedisSettingsBindToSpringBootRedisProperties() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new MapPropertySource("review-env", Map.of(
                "REDIS_HOST", "cache.example.invalid",
                "REDIS_PORT", "6380",
                "REDIS_PASSWORD", "",
                "REDIS_SSL_ENABLED", "true",
                "REDIS_CONNECT_TIMEOUT", "1500ms"
        )));
        environment.getPropertySources().addLast(
                new PropertiesPropertySource("application-prod.yml", load("application-prod.yml"))
        );

        RedisProperties redis = Binder.get(environment)
                .bind("spring.data.redis", RedisProperties.class)
                .orElseThrow(() -> new AssertionError("Redis properties did not bind"));

        assertThat(redis.getHost()).isEqualTo("cache.example.invalid");
        assertThat(redis.getPort()).isEqualTo(6380);
        assertThat(redis.getPassword()).isEmpty();
        assertThat(redis.getSsl().isEnabled()).isTrue();
        assertThat(redis.getConnectTimeout()).isEqualTo(Duration.ofMillis(1500));
    }

    private Properties load(String resource) {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource(resource));
        Properties properties = factory.getObject();
        assertThat(properties).isNotNull();
        return properties;
    }
}
