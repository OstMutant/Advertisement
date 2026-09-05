package org.ost.integrationtests.support;

import java.util.Optional;
import lombok.NonNull;
import org.ost.platform.advertisement.spi.AdvertisementPort;
import org.ost.platform.attachment.spi.AttachmentPort;
import org.ost.platform.audit.spi.AuditPort;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.providerprofile.spi.ProviderProfilePort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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
 * <p>The empty {@code ComponentFactory<AuditPort>}/{@code ComponentFactory<AttachmentPort>}/
 * {@code ComponentFactory<AdvertisementPort>}/{@code ComponentFactory<ProviderProfilePort>} beans
 * represent those starters being absent from the test classpath — the same "optional starter"
 * shape services see in production via {@code ObjectProvider}, not a stub. Domain starters whose
 * services depend on additional optional ports (e.g. {@code TaxonPort}) must add their own empty
 * {@code ComponentFactory} bean in the consuming test, not here — this class only covers the ports
 * every repository test has hit so far.</p>
 *
 * <p>{@link RepositoryTestAutoConfig}'s explicit class list, instead of
 * {@code @EnableAutoConfiguration}: the latter pulls in every {@code @AutoConfiguration} found on
 * the whole classpath, not just what this class actually needs. {@code integration-tests}
 * deliberately keeps adding new starter dependencies over time (Batches 2/3 — see
 * {@code integration-tests/CLAUDE.md}), and each one bundles its own {@code *AutoConfiguration}
 * that {@code @EnableAutoConfiguration} would silently cascade into every test using this class,
 * regardless of whether that test wants it. Confirmed directly: adding
 * {@code audit-spring-boot-starter} as a dependency (for {@code UserServiceRestoreTest}) broke
 * {@code AdvertisementRepositoryTest}/{@code TaxonRepositoryTest}/
 * {@code TaxonPortTranslationFallbackTest}/{@code UserRepositoryTest} in a full-suite run — the
 * cascaded-in {@code AuditAutoConfiguration}'s {@code defaultAuditPort} bean failed to construct
 * (missing {@code CurrentActorHook}), and a single-class {@code -Dtest=X} run never surfaces this
 * since it depends only on what's on the classpath, not on which test is selected. An
 * {@code exclude = AuditAutoConfiguration.class} deny-list fixed that one occurrence, but the same
 * class of break will recur for every future starter dependency unless someone remembers to add
 * another exclude each time — a silent, easy-to-forget maintenance tax. The explicit allow-list
 * below is immune to this by construction: adding a new starter to {@code pom.xml} can never
 * again affect this class's own Spring context, because nothing is pulled in implicitly. The list
 * covers exactly the Spring Boot JDBC/Liquibase/Transaction infrastructure every
 * {@code *RepositoryTest} needs (Spring Boot 4.0.6 split what used to be one
 * {@code spring-boot-autoconfigure} jar into per-feature modules —
 * {@code spring-boot-jdbc}/{@code spring-boot-data-jdbc}/{@code spring-boot-liquibase}/
 * {@code spring-boot-transaction} — so this list spans four different packages, not one).
 * Domain-starter autoconfiguration (e.g. {@code AdvertisementAutoConfiguration}) is never in this
 * list — those are always passed explicitly via each test's own
 * {@code @SpringBootTest(classes = {...})}, which is the whole point.</p>
 */
@TestConfiguration
@RepositoryTestAutoConfig
@EnableJdbcAuditing
public class RepositoryTestSupport {

    // @ConditionalOnMissingBean: a test that supplies its own request-scoped AuditorAware (e.g. a Level 3 REST scenario) already provides this bean.
    @Bean
    @ConditionalOnMissingBean(AuditorAware.class)
    public MutableAuditorAware auditorAware() {
        return new MutableAuditorAware();
    }

    @Bean
    public ComponentFactory<AuditPort> auditPortFactory(ObjectProvider<AuditPort> provider) {
        return new ComponentFactory<>(provider);
    }

    // @ConditionalOnMissingBean: a test that also includes the real AttachmentAutoConfiguration (e.g. a Level 3 REST scenario) already provides this bean.
    @Bean
    @ConditionalOnMissingBean
    public ComponentFactory<AttachmentPort> attachmentPortFactory(ObjectProvider<AttachmentPort> provider) {
        return new ComponentFactory<>(provider);
    }

    // @ConditionalOnMissingBean: AdvertisementRepositoryTest's own AdvertisementAutoConfiguration already provides this bean.
    @Bean
    @ConditionalOnMissingBean
    public ComponentFactory<AdvertisementPort> advertisementPortFactory(ObjectProvider<AdvertisementPort> provider) {
        return new ComponentFactory<>(provider);
    }

    // @ConditionalOnMissingBean: ProviderProfileRepositoryTest's own ProviderProfileAutoConfiguration already provides this bean.
    @Bean
    @ConditionalOnMissingBean
    public ComponentFactory<ProviderProfilePort> providerProfilePortFactory(ObjectProvider<ProviderProfilePort> provider) {
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
        public @org.jspecify.annotations.NonNull Optional<Long> getCurrentAuditor() {
            return Optional.ofNullable(currentUserId);
        }
    }
}
