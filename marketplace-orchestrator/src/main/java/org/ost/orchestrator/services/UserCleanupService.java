package org.ost.orchestrator.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.platform.user.spi.UserAccountPort;
import org.ost.platform.user.spi.UserPort;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

/** Scheduled retention purge: finds soft-deleted accounts past the retention window and permanently deletes whichever aren't still referenced by an advertisement or provider profile. */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserCleanupService {

    private final UserPort                      userPort;
    private final UserAccountPort                accountPort;
    private final UserPurgeEligibilityService    eligibilityService;

    public void cleanup(int retentionDays) {
        Set<Long> candidates = userPort.findIdsDeletedOlderThan(retentionDays);
        if (candidates.isEmpty()) return;

        eligibilityService.clearAdvertisementReferences(candidates);
        Set<Long> stillReferenced = eligibilityService.findStillReferencedIds(candidates);

        Set<Long> purgeable = candidates.stream().filter(id -> !stillReferenced.contains(id))
                .collect(Collectors.toSet());
        if (!purgeable.isEmpty()) {
            accountPort.purge(purgeable);
        }
        log.info("User cleanup finished: purged={}, skipped={}", purgeable.size(), stillReferenced.size());
    }
}
