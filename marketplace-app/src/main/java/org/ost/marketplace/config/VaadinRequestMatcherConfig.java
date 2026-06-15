package org.ost.marketplace.config;

import com.vaadin.flow.server.HandlerHelper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
public class VaadinRequestMatcherConfig {

    @Bean
    public RequestMatcher vaadinInternalRequestMatcher() {
        return request -> HandlerHelper.isFrameworkInternalRequest("/", request);
    }
}
