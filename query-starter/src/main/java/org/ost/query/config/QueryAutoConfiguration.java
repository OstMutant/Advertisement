package org.ost.query.config;

import jakarta.validation.Validator;
import org.ost.platform.core.ComponentFactory;
import org.ost.query.ui.filter.ValidationService;
import org.springframework.beans.factory.InjectionPoint;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Scope;
import org.springframework.core.ResolvableType;

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
    @Scope("prototype")
    @ConditionalOnMissingBean
    public ComponentFactory<?> componentFactory(InjectionPoint injectionPoint, ConfigurableListableBeanFactory beanFactory) {
        ResolvableType type = injectionPoint.getField() != null
                ? ResolvableType.forField(injectionPoint.getField())
                : ResolvableType.forMethodParameter(injectionPoint.getMethodParameter());
        Class<?> beanClass = type.getGeneric(0).toClass();
        return new ComponentFactory<>(beanFactory.getBeanProvider(ResolvableType.forClass(beanClass)));
    }
}
