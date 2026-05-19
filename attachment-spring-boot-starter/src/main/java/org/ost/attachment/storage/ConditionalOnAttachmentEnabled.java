package org.ost.attachment.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.annotation.*;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnProperty(name = "attachment.enabled", havingValue = "true", matchIfMissing = true)
public @interface ConditionalOnAttachmentEnabled {
}
