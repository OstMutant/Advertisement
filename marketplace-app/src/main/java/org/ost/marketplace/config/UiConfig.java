package org.ost.marketplace.config;

import org.ost.platform.ui.ComponentFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UiConfig {

    @Bean
    @ConditionalOnMissingBean
    public ComponentFactory componentFactory(ConfigurableListableBeanFactory beanFactory) {
        return new ComponentFactory(beanFactory);
    }
}
