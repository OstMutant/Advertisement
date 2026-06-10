package org.ost.marketplace.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

@Configuration
public class MessageSourceConfig {

    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource source = new ReloadableResourceBundleMessageSource();
        source.setBasenames("classpath:i18n/messages", "classpath:i18n/audit-messages", "classpath:i18n/attachment-messages");
        source.setDefaultEncoding("UTF-8");
        return source;
    }
}
