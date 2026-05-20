package org.ost.marketplace.services.audit;

import org.ost.marketplace.repository.advertisement.AdvertisementDescriptor;
import org.ost.marketplace.repository.user.UserDescriptor;
import org.ost.platform.audit.spi.AuditEntityExistenceChecker;
import org.ost.platform.core.model.EntityType;
import org.ost.sqlengine.RepositoryCustom;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class AuditEntityExistenceCheckerImpl implements AuditEntityExistenceChecker {

    private final RepositoryCustom repo;

    public AuditEntityExistenceCheckerImpl(JdbcClient jdbcClient) {
        this.repo = new RepositoryCustom(jdbcClient);
    }

    @Override
    public Set<Long> findExisting(EntityType entityType, Set<Long> entityIds) {
        if (entityIds.isEmpty()) return Set.of();
        Long[] ids = entityIds.toArray(new Long[0]);
        List<Long> found = switch (entityType) {
            case ADVERTISEMENT -> repo.findAll(
                    AdvertisementDescriptor.Read.SELECT_EXISTING_IDS,
                    AdvertisementDescriptor.Read.existingIdsParams(ids),
                    (rs, _) -> rs.getLong("id"));
            case USER, USER_SETTINGS -> repo.findAll(
                    UserDescriptor.Read.SELECT_EXISTING_IDS,
                    UserDescriptor.Read.idsParams(ids),
                    (rs, _) -> rs.getLong("id"));
        };
        return Set.copyOf(found);
    }
}
