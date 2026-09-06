package org.ost.integrationtests.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.ost.advertisement.config.AdvertisementAutoConfiguration;
import org.ost.apikey.config.ApiKeyAutoConfiguration;
import org.ost.attachment.config.AttachmentAutoConfiguration;
import org.ost.orchestrator.config.OrchestratorAutoConfiguration;
import org.ost.provider.config.ProviderProfileAutoConfiguration;
import org.ost.restapi.config.RestApiAutoConfiguration;
import org.ost.taxon.config.TaxonAutoConfiguration;
import org.ost.user.config.UserAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

/** Every Level 3 test class uses this exact same fixed class set, so Spring caches one shared context/connection pool instead of one per test class (a distinct set per class exhausted real Postgres connections). */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(classes = {
        UserAutoConfiguration.class,
        ApiKeyAutoConfiguration.class,
        TaxonAutoConfiguration.class,
        AttachmentAutoConfiguration.class,
        AdvertisementAutoConfiguration.class,
        ProviderProfileAutoConfiguration.class,
        OrchestratorAutoConfiguration.class,
        RestApiAutoConfiguration.class,
        RestApiTestSupport.class,
        RepositoryTestSupport.class
})
@RestApiTestAutoConfig
public @interface Level3ScenarioTest {
}
