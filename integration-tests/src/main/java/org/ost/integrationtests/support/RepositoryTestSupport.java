package org.ost.integrationtests.support;

import java.util.Optional;
import lombok.NonNull;
import org.ost.platform.attachment.spi.AttachmentPort;
import org.ost.platform.audit.spi.AuditPort;
import org.ost.platform.core.ComponentFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jdbc.repository.config.EnableJdbcAuditing;

/**
 * Shared Spring wiring for {@code *RepositoryTest} classes, by analogy with Playwright's
 * {@code _flows/*.flow.js} — extracted once two or more repository tests needed the same
 * boilerplate. Add to a test's {@code @SpringBootTest(classes = {...})} instead of redeclaring
 * {@link MutableAuditorAware} or the empty {@link ComponentFactory} beans per test class.
 *
 * <p>The empty {@code ComponentFactory<AuditPort>}/{@code ComponentFactory<AttachmentPort>} beans
 * represent audit-spring-boot-starter/attachment-spring-boot-starter being absent from the test
 * classpath — the same "optional starter" shape services see in production via
 * {@code ObjectProvider}, not a stub. Domain starters whose services depend on additional optional
 * ports (e.g. {@code TaxonPort}) must add their own empty {@code ComponentFactory} bean in the
 * consuming test, not here — this class only covers the ports every repository test has hit so
 * far.</p>
 */
@TestConfiguration
@EnableAutoConfiguration
@EnableJdbcAuditing
public class RepositoryTestSupport {

    @Bean
    public MutableAuditorAware auditorAware() {
        return new MutableAuditorAware();
    }

    @Bean
    public ComponentFactory<AuditPort> auditPortFactory(ObjectProvider<AuditPort> provider) {
        return new ComponentFactory<>(provider);
    }

    @Bean
    public ComponentFactory<AttachmentPort> attachmentPortFactory(ObjectProvider<AttachmentPort> provider) {
        return new ComponentFactory<>(provider);
    }

    /** {@code @CreatedBy} needs the real id of a row that already exists (the FK target), so the
     *  auditor can't be a fixed constant — it's set per test after creating the fixture actor. */
    public static class MutableAuditorAware implements AuditorAware<Long> {
        private Long currentUserId;

        public void setCurrentUserId(@NonNull Long id) {
            this.currentUserId = id;
        }

        @Override
        public Optional<Long> getCurrentAuditor() {
            return Optional.ofNullable(currentUserId);
        }
    }
}
