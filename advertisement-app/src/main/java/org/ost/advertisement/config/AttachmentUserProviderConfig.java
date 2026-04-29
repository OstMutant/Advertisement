package org.ost.advertisement.config;

import lombok.RequiredArgsConstructor;
import org.ost.advertisement.events.spi.AttachmentCurrentUserProvider;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.services.auth.AuthContextService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

@Configuration
@RequiredArgsConstructor
public class AttachmentUserProviderConfig {

    private final AuthContextService authContextService;

    @Bean
    public AttachmentCurrentUserProvider attachmentCurrentUserProvider() {
        return () -> authContextService.getCurrentUser().map(User::getId);
    }
}
