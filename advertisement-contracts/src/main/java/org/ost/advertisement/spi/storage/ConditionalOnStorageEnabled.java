package org.ost.advertisement.spi.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.annotation.*;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnProperty(name = "storage.s3.enabled", havingValue = "true", matchIfMissing = true)
public @interface ConditionalOnStorageEnabled {
}
