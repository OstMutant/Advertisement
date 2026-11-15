package org.ost.integrationtests.level2.orchestrator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.ost.integrationtests.AbstractPostgresIntegrationTest;
import org.ost.integrationtests.support.RepositoryTestAutoConfig;
import org.ost.orchestrator.services.AdvertisementReadService;
import org.ost.orchestrator.services.ApiKeyManagementService;
import org.ost.orchestrator.services.AttachmentMediaService;
import org.ost.orchestrator.services.AuditQueryService;
import org.ost.orchestrator.services.ContactAccessService;
import org.ost.orchestrator.services.FeedbackAccessService;
import org.ost.orchestrator.services.ProviderProfileReadService;
import org.ost.orchestrator.services.TaxonCatalogService;
import org.ost.orchestrator.spi.CurrentUserHook;
import org.ost.orchestrator.spi.SessionActorHook;
import org.ost.orchestrator.spi.SettingsChangeHook;
import org.ost.orchestrator.spi.UiLabelHook;
import org.ost.orchestrator.spi.UiLocaleHook;
import org.ost.platform.advertisement.spi.AdvertisementPort;
import org.ost.platform.apikey.spi.ApiKeyPort;
import org.ost.platform.attachment.spi.AttachmentAuditPort;
import org.ost.platform.attachment.spi.AttachmentPort;
import org.ost.platform.audit.spi.AuditPort;
import org.ost.platform.contact.spi.ContactPort;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.feedback.spi.FeedbackPort;
import org.ost.platform.promotion.spi.PromotionPlacementPort;
import org.ost.platform.providerprofile.spi.ProviderProfilePort;
import org.ost.platform.taxon.spi.TaxonPort;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.core.ResolvableType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Level 2: the orchestrator and the domain starters start against a real Postgres, once with every starter present and once for
 * each optional starter with its configuration left out; what that starter provided then reports itself unavailable instead of failing the start.
 */
class OptionalStarterAbsenceTest extends AbstractPostgresIntegrationTest {

    /** One optional starter: its name (the test's display name), its base package (its configuration is the one left out), the ports it provides, and the checks that read availability from an orchestrator service (none for promotion). */
    private record Starter(String name, String basePackage, List<Class<?>> ports, List<Predicate<ApplicationContext>> availabilityChecks) {
        @Override
        public String toString() {
            return name;
        }
    }

    static Stream<Starter> optionalStarters() {
        return Stream.of(
                new Starter("audit", "org.ost.audit", List.of(AuditPort.class), List.of(c -> c.getBean(AuditQueryService.class).isAvailable())),
                new Starter("attachment", "org.ost.attachment", List.of(AttachmentPort.class, AttachmentAuditPort.class),
                        List.of(c -> c.getBean(AttachmentMediaService.class).isAvailable())),
                new Starter("advertisement", "org.ost.advertisement", List.of(AdvertisementPort.class),
                        List.of(c -> c.getBean(AdvertisementReadService.class).isAvailable())),
                new Starter("provider-profile", "org.ost.provider", List.of(ProviderProfilePort.class),
                        List.of(c -> c.getBean(ProviderProfileReadService.class).isAvailable())),
                new Starter("apikey", "org.ost.apikey", List.of(ApiKeyPort.class),
                        List.of(c -> c.getBean(ApiKeyManagementService.class).isAvailable())),
                new Starter("contact", "org.ost.contact", List.of(ContactPort.class),
                        List.of(c -> c.getBean(ContactAccessService.class).isAvailable())),
                new Starter("feedback", "org.ost.feedback", List.of(FeedbackPort.class),
                        List.of(c -> c.getBean(FeedbackAccessService.class).isAvailable())),
                new Starter("promotion", "org.ost.promotion", List.of(PromotionPlacementPort.class), List.of()),
                new Starter("taxon", "org.ost.taxon", List.of(TaxonPort.class),
                        List.of(c -> c.getBean(TaxonCatalogService.class).isAvailable())));
    }

    @ParameterizedTest(name = "with every starter present, the {0} starter''s ports report available")
    @MethodSource("optionalStarters")
    void everyPortReportsAvailableWhenItsStarterIsPresent(Starter starter) {
        runner(null).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(starter.ports()).allSatisfy(port -> assertThat(factoryOf(context, port).findIfAvailable()).isPresent());
        });
    }

    @ParameterizedTest(name = "starts without the {0} starter")
    @MethodSource("optionalStarters")
    void contextStartsWithoutOneOptionalStarter(Starter starter) {
        runner(starter).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(starter.ports()).allSatisfy(port -> assertThat(factoryOf(context, port).findIfAvailable()).isEmpty());
            assertThat(starter.availabilityChecks())
                    .allSatisfy(check -> assertThat(check.test(context)).as("the orchestrator service reports available").isFalse());
        });
    }

    @Test
    void startsWithEveryStarterPresent() {
        runner(null).run(context -> assertThat(context).hasNotFailed());
    }

    private static ComponentFactory<?> factoryOf(ApplicationContext context, Class<?> port) {
        return (ComponentFactory<?>) context.getBeanProvider(ResolvableType.forClassWithGenerics(ComponentFactory.class, port)).getObject();
    }

    private ApplicationContextRunner runner(Starter hidden) {
        List<Class<?>> modules = new ArrayList<>();
        for (String name : ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader()).getCandidates()) {
            if (isModuleUnderTest(name) && (hidden == null || !name.startsWith(hidden.basePackage() + "."))) {
                modules.add(load(name));
            }
        }
        // The modules go in as plain configurations so their own Liquibase beans exist before Boot's default Liquibase bean is considered.
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RepositoryTestAutoConfig.class.getAnnotation(ImportAutoConfiguration.class).value()))
                .withUserConfiguration(modules.toArray(Class<?>[]::new))
                .withBean(UiLabelHook.class, () -> mock(UiLabelHook.class))
                .withBean(UiLocaleHook.class, () -> mock(UiLocaleHook.class))
                .withBean(CurrentUserHook.class, () -> mock(CurrentUserHook.class))
                .withBean(SessionActorHook.class, () -> mock(SessionActorHook.class))
                .withBean(SettingsChangeHook.class, () -> mock(SettingsChangeHook.class))
                .withPropertyValues(
                        "spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "spring.datasource.username=" + POSTGRES.getUsername(),
                        "spring.datasource.password=" + POSTGRES.getPassword(),
                        "spring.datasource.hikari.maximum-pool-size=2",
                        "storage.s3.endpoint=http://localhost:9000",
                        "storage.s3.region=us-east-1",
                        "storage.s3.access-key=test",
                        "storage.s3.secret-key=test",
                        "storage.s3.bucket=test",
                        "storage.s3.public-url=http://localhost:9000/test",
                        "app.public-base-url=http://localhost:8080");
    }

    /** The orchestrator and the starters; the REST API and MCP adapters are not part of this check. */
    private static boolean isModuleUnderTest(String autoConfigurationName) {
        return autoConfigurationName.startsWith("org.ost.orchestrator.")
                || Stream.of("audit", "attachment", "user", "advertisement", "taxon", "provider", "apikey", "contact", "feedback", "promotion")
                        .anyMatch(p -> autoConfigurationName.startsWith("org.ost." + p + "."));
    }

    private static Class<?> load(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }
}
