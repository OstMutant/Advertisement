package org.ost.integrationtests;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;

/** Shared singleton Postgres container for every {@code *RepositoryTest}; its image tag and project label come from the repo-root {@code .env} through {@link SharedEnvConfig}. */
@Tag("testcontainers")
public abstract class AbstractPostgresIntegrationTest {

    /** Forces a fixed host port for sandboxes whose Docker cannot reach dynamically published ports; unset on a normal machine. */
    private static final String FIXED_PORT_ENV = "INTEGRATION_TESTS_POSTGRES_FIXED_PORT";

    @ServiceConnection
    protected static final PostgreSQLContainer POSTGRES = createContainer();

    static {
        POSTGRES.start();
    }

    private static PostgreSQLContainer createContainer() {
        PostgreSQLContainer container = new PostgreSQLContainer(SharedEnvConfig.require("POSTGRES_IMAGE"));
        String[] label = SharedEnvConfig.require("PROJECT_LABEL").split("=", 2);
        container.withLabel(label[0], label[1]);
        // Every cached Spring context holds a Hikari pool, so the 100-connection default runs out.
        container.withCommand("postgres", "-c", "fsync=off", "-c", "max_connections=300");
        String fixedPort = System.getenv(FIXED_PORT_ENV);
        if (fixedPort != null && !fixedPort.isBlank()) {
            container.setPortBindings(List.of(fixedPort + ":5432"));
        }
        return container;
    }
}
