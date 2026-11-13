package org.ost.promotion.config;

import liquibase.integration.spring.SpringLiquibase;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.promotion.spi.PromotionPlacementPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;

import javax.sql.DataSource;

/** Auto-configures the Promotion domain -- its own Liquibase migration plus the {@code PromotionPlacementPort} component factory bean. */
@AutoConfiguration(afterName = "org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration")
@ConditionalOnClass(DataSource.class)
@ComponentScan({"org.ost.promotion.spi", "org.ost.promotion.services", "org.ost.promotion.repository"})
@EnableJdbcRepositories(basePackages = "org.ost.promotion.repository")
public class PromotionAutoConfiguration {

    @Bean("promotionLiquibase")
    @ConditionalOnMissingBean(name = "promotionLiquibase")
    public SpringLiquibase promotionLiquibase(DataSource dataSource) {
        SpringLiquibase liq = new SpringLiquibase();
        liq.setDataSource(dataSource);
        liq.setChangeLog("classpath:db/promotion-changelog/promotion-changelog-master.xml");
        return liq;
    }

    @Bean
    @ConditionalOnMissingBean
    public ComponentFactory<PromotionPlacementPort> promotionPlacementPortFactory(ObjectProvider<PromotionPlacementPort> p) {
        return new ComponentFactory<>(p);
    }
}
