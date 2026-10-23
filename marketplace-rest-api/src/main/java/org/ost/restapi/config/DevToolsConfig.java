package org.ost.restapi.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Serves the developer-only API flow runner page under {@code /dev-tools/api-flows/}; the path is not registered at all unless {@code marketplace.dev-tools.enabled} is true. */
@Configuration
@ConditionalOnProperty(name = "marketplace.dev-tools.enabled", havingValue = "true")
public class DevToolsConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/dev-tools/api-flows/**").addResourceLocations("classpath:/dev-tools/api-flows/");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/dev-tools/api-flows/").setViewName("forward:/dev-tools/api-flows/index.html");
    }
}
