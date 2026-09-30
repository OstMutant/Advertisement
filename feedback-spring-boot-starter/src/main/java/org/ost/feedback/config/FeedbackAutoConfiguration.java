package org.ost.feedback.config;

import liquibase.integration.spring.SpringLiquibase;
import org.ost.platform.feedback.spi.FeedbackPort;
import org.ost.platform.core.ComponentFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;

import javax.sql.DataSource;

/** Auto-configures the Feedback domain -- its own Liquibase migration plus the {@code FeedbackPort} component factory bean. */
@AutoConfiguration(afterName = "org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration")
@ConditionalOnClass(DataSource.class)
@ComponentScan({"org.ost.feedback.spi", "org.ost.feedback.services", "org.ost.feedback.repository"})
@EnableJdbcRepositories(basePackages = "org.ost.feedback.repository")
public class FeedbackAutoConfiguration {

    @Bean("feedbackLiquibase")
    @ConditionalOnMissingBean(name = "feedbackLiquibase")
    public SpringLiquibase feedbackLiquibase(DataSource dataSource) {
        SpringLiquibase liq = new SpringLiquibase();
        liq.setDataSource(dataSource);
        liq.setChangeLog("classpath:db/feedback-changelog/feedback-changelog-master.xml");
        return liq;
    }

    @Bean
    @ConditionalOnMissingBean
    public ComponentFactory<FeedbackPort> feedbackPortFactory(ObjectProvider<FeedbackPort> p) {
        return new ComponentFactory<>(p);
    }
}
