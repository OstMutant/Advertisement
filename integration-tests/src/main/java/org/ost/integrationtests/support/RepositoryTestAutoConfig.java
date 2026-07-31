package org.ost.integrationtests.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.data.jdbc.autoconfigure.DataJdbcRepositoriesAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;

/** Composed {@code @ImportAutoConfiguration} allow-list shared by {@link RepositoryTestSupport} and every {@code *RepositoryTest}'s local {@code TestConfig}. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ImportAutoConfiguration({
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        JdbcClientAutoConfiguration.class,
        JdbcTemplateAutoConfiguration.class,
        DataJdbcRepositoriesAutoConfiguration.class,
        LiquibaseAutoConfiguration.class,
        TransactionAutoConfiguration.class,
        ConfigurationPropertiesAutoConfiguration.class
})
public @interface RepositoryTestAutoConfig {
}
