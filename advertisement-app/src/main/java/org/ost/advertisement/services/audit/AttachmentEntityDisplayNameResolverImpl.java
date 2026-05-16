package org.ost.advertisement.services.audit;

import lombok.RequiredArgsConstructor;
import org.ost.advertisement.attachment.spi.AttachmentEntityDisplayNameResolver;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AttachmentEntityDisplayNameResolverImpl implements AttachmentEntityDisplayNameResolver {

    private final JdbcClient jdbcClient;

    @Override
    public Map<Long, String> resolveDisplayNames(Set<Long> entityIds) {
        if (entityIds.isEmpty()) return Map.of();
        return jdbcClient.sql("SELECT id, title FROM advertisement WHERE id = ANY(:ids)")
                .param("ids", entityIds.toArray(new Long[0]))
                .query((rs, _) -> Map.entry(rs.getLong("id"), rs.getString("title")))
                .list()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
