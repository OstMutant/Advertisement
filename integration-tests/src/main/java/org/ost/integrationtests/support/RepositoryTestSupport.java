package org.ost.integrationtests.support;

import java.util.Optional;
import lombok.NonNull;
import org.ost.audit.config.AuditAutoConfiguration;
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
 *
 * <p>{@code @EnableAutoConfiguration} explicitly excludes {@link AuditAutoConfiguration}: once
 * {@code audit-spring-boot-starter} became a real {@code integration-tests} dependency (for
 * {@code UserServiceRestoreTest}), the classpath-wide autoconfiguration cascade started pulling
 * in the real {@code AuditAutoConfiguration} for every test using this class too — its
 * {@code defaultAuditPort} bean then fails to construct (needs a {@link
 * org.ost.platform.core.spi.CurrentActorHook} this class never provides), breaking every
 * {@code *RepositoryTest} that doesn't actually want audit wired in. Confirmed directly: a full
 * {@code mvn -pl integration-tests test} run (all classes together, not a single
 * {@code -Dtest=X}) failed {@code AdvertisementRepositoryTest}/{@code TaxonRepositoryTest}/
 * {@code TaxonPortTranslationFallbackTest}/{@code UserRepositoryTest} with "Parameter 1 of method
 * defaultAuditPort ... required a bean of type CurrentActorHook that could not be found" — a
 * single-class run never surfaces this since it doesn't depend on {@code -Dtest} filtering, only
 * on whether the audit-starter JAR is on the classpath at all, which it always is once added to
 * {@code pom.xml}. The exclude restores this class's original "audit starter absent" premise
 * regardless of what other tests in the same reactor need.</p>
 */
@TestConfiguration
@EnableAutoConfiguration(exclude = AuditAutoConfiguration.class)
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
