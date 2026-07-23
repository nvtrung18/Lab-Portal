package com.web.labportalbackend.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

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

    private Properties load(String resource) {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource(resource));
        Properties properties = factory.getObject();
        assertThat(properties).isNotNull();
        return properties;
    }
}
