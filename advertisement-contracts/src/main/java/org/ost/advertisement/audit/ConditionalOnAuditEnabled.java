package org.ost.advertisement.audit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.annotation.*;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnProperty(name = "audit.enabled", havingValue = "true", matchIfMissing = true)
public @interface ConditionalOnAuditEnabled {}
