package org.ost.integrationtests;

import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the Batch 0 scaffolding: the shared container starts, and a real Liquibase changelog
 * applies against it. Deliberately no {@code @SpringBootTest} — this class only needs the
 * container and Liquibase, not a Spring context; real repository tests (Batch 1+) do use
 * {@code @SpringBootTest}.
 */
class PostgresContainerSmokeTest extends AbstractPostgresIntegrationTest {

    @Test
    void containerStartsAndChangelogApplies() throws Exception {
        try (Connection connection = POSTGRES.createConnection("")) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            // Not try-with-resources: Liquibase.close() closes the underlying Database, which
            // closes this same JDBC connection — needed below for the verification query.
            new Liquibase("db/integration-tests-changelog/smoke-changelog.xml",
                    new ClassLoaderResourceAccessor(), database).update();

            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM smoke_test")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt(1)).isZero();
            }
        }
    }
}
