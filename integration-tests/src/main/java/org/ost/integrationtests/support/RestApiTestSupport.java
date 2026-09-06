package org.ost.integrationtests.support;

import java.util.Locale;
import java.util.Optional;
import org.ost.orchestrator.spi.CurrentLocaleHook;
import org.ost.orchestrator.spi.CurrentUserHook;
import org.ost.orchestrator.spi.SessionActorHook;
import org.ost.orchestrator.spi.SettingsChangeHook;
import org.ost.orchestrator.spi.UiLabelHook;
import org.ost.platform.advertisement.model.AdKind;
import org.ost.platform.user.dto.UserDto;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Shared Level 3 wiring: marketplace-app-only forwarder Hook stubs (REST never calls these paths) plus a request-scoped {@link AuditorAware} reading the resolved bearer-key principal. */
@TestConfiguration
public class RestApiTestSupport {

    // @CreatedBy/@LastModifiedBy read this per insert -- mirrors marketplace-app's JdbcAuditingConfig, off the resolved bearer-key principal instead of a Vaadin session.
    @Bean
    public AuditorAware<Long> auditorAware() {
        return () -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            return auth != null && auth.getPrincipal() instanceof Long id ? Optional.of(id) : Optional.empty();
        };
    }

    @Bean
    public SessionActorHook sessionActorHook() {
        return Optional::empty;
    }

    @Bean
    public CurrentLocaleHook currentLocaleHook() {
        return () -> Locale.ENGLISH;
    }

    @Bean
    public CurrentUserHook currentUserHook() {
        return new CurrentUserHook() {
            @Override
            public Optional<UserDto> getCurrentUser() {
                return Optional.empty();
            }

            @Override
            public Optional<String> getCurrentUserLocale() {
                return Optional.empty();
            }
        };
    }

    @Bean
    public SettingsChangeHook settingsChangeHook() {
        return (userId, settings) -> { };
    }

    @Bean
    public UiLabelHook uiLabelHook() {
        return new UiLabelHook() {
            @Override
            public String translateActorDeletedSuffix(String actorName) {
                return actorName;
            }

            @Override
            public String labelFor(AdKind kind) {
                return kind.name();
            }

            @Override
            public String markDeleted(String name) {
                return name;
            }

            @Override
            public String noMediaPlaceholder() {
                return "";
            }
        };
    }
}
