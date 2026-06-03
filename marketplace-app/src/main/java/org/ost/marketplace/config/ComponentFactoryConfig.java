package org.ost.marketplace.config;

import org.ost.platform.core.ComponentFactory;
import org.springframework.beans.factory.InjectionPoint;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.core.ResolvableType;

@Configuration
public class ComponentFactoryConfig {

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
