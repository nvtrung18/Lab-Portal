package com.web.labportalbackend.admin.website.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Constraint(validatedBy = WebsiteHtmlValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface SafeWebsiteHtml {
    boolean proseOnly() default false;
    String message() default "Website article contains unsupported HTML or an invalid image/link URL.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
