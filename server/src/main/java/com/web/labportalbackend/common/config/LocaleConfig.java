package com.web.labportalbackend.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.List;
import java.util.Locale;

@Configuration
public class LocaleConfig {

    private final String configuredDefaultLocale;

    public LocaleConfig(@Value("${app.i18n.default-locale:vi}") String configuredDefaultLocale) {
        this.configuredDefaultLocale = configuredDefaultLocale;
    }

    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setDefaultLocale(defaultLocale());
        resolver.setSupportedLocales(List.of(
                Locale.forLanguageTag("vi"),
                Locale.ENGLISH
        ));
        return resolver;
    }

    @Bean(name = "messageSource")
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
        messageSource.setBasenames("classpath:messages/messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        return messageSource;
    }

    private Locale defaultLocale() {
        return "en".equalsIgnoreCase(configuredDefaultLocale == null
                ? ""
                : configuredDefaultLocale.trim())
                ? Locale.ENGLISH
                : Locale.forLanguageTag("vi");
    }
}
