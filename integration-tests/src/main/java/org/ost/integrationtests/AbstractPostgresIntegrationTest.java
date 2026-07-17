package org.ost.integrationtests;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;

/**
 * Shared singleton-container base class: one Postgres instance (image tag sourced from the
 * repo-root {@code .env}, shared with {@code docker-compose.db.yml} — see
 * {@link SharedEnvConfig}), started once for the whole {@code mvn test} reactor run and reused
 * across every subclassing test class, instead of one container per starter. Deliberately not
 * annotated with {@code @Testcontainers}/{@code @Container} — that ties container lifecycle to
 * per-class before/after-all hooks, which restarts it once per test class. Testcontainers' Ryuk
 * reaper cleans this up on JVM exit; there is no explicit stop.
 * <p>
 * {@code @Tag("testcontainers")} here is inherited by every subclass (JUnit 5 tags declared on a
 * superclass apply to all subclasses) -- this is the single point that keeps every Docker-backed
 * test out of a plain {@code mvn test} by default, via {@code excludedGroups} in
 * {@code integration-tests/pom.xml} (improvement-047). A new {@code *RepositoryTest} extending
 * this class is tagged automatically; nothing to remember per test class.
 */
@Tag("testcontainers")
public abstract class AbstractPostgresIntegrationTest {

    /**
     * Some sandboxed Docker environments (e.g. the claude-dev container this project is
     * developed in) can create containers via the Docker socket but cannot reach a
     * dynamically-assigned published port from the test JVM — only statically published ones.
     * Setting this env var forces a fixed host-port binding as a workaround. Unset on a normal
     * developer machine, where Testcontainers' default random-port assignment just works. See
     * features/issues/improvement-027-unit-testcontainers-test-layer.md.
     */
    private static final String FIXED_PORT_ENV = "INTEGRATION_TESTS_POSTGRES_FIXED_PORT";

    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRES = createContainer();

    static {
        POSTGRES.start();
    }

    private static PostgreSQLContainer<?> createContainer() {
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>(SharedEnvConfig.require("POSTGRES_IMAGE"));
        String fixedPort = System.getenv(FIXED_PORT_ENV);
        if (fixedPort != null && !fixedPort.isBlank()) {
            container.setPortBindings(List.of(fixedPort + ":5432"));
        }
        return container;
    }
}
