package org.ost.query.config;

import jakarta.validation.Validator;
import org.ost.platform.ui.ComponentFactory;
import org.ost.query.ui.filter.ValidationService;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

@AutoConfiguration
@ComponentScan("org.ost.query.ui")
@ConditionalOnClass(Validator.class)
public class QueryAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ValidationService<?> validationService(Validator validator) {
        return new ValidationService<>(validator);
    }

    @Bean
    @ConditionalOnMissingBean
    public ComponentFactory componentFactory(ConfigurableListableBeanFactory beanFactory) {
        return new ComponentFactory(beanFactory);
    }
}
