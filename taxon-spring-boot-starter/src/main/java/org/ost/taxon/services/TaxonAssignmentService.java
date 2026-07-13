package org.ost.taxon.services;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.taxon.spi.TaxonAuditHook;
import org.ost.platform.taxon.spi.TaxonAuditHook.AssignmentChange;
import org.ost.taxon.entities.TaxonAssignment;
import org.ost.taxon.repository.TaxonAssignmentRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaxonAssignmentService {

    private final TaxonAssignmentRepository      assignmentRepository;
    private final ObjectProvider<TaxonAuditHook> auditHook;

    public void assign(@NonNull EntityType entityType, @NonNull Long entityId,
                       @NonNull Long taxonId, Long actorId) {
        log.info("Taxon assign: entityType={}, entityId={}, taxonId={}", entityType, entityId, taxonId);
        assignmentRepository.assign(entityType.name(), entityId, taxonId, actorId);
        auditHook.ifAvailable(h -> h.onAssignmentChanged(entityType, entityId, taxonId, AssignmentChange.ASSIGNED));
    }

    public void unassign(@NonNull EntityType entityType, @NonNull Long entityId,
                         @NonNull Long taxonId, Long actorId) {
        log.info("Taxon unassign: entityType={}, entityId={}, taxonId={}", entityType, entityId, taxonId);
        assignmentRepository.unassign(entityType.name(), entityId, taxonId);
        auditHook.ifAvailable(h -> h.onAssignmentChanged(entityType, entityId, taxonId, AssignmentChange.UNASSIGNED));
    }

    @Transactional
    public void replaceAssignments(@NonNull EntityType entityType, @NonNull Long entityId,
                                   @NonNull Set<Long> newTaxonIds, Long actorId) {
        Set<Long> current = assignmentRepository
                .findAllByEntity(entityType.name(), entityId)
                .stream()
                .map(TaxonAssignment::getTaxonId)
                .collect(Collectors.toSet());

        Set<Long> toAdd    = newTaxonIds.stream().filter(id -> !current.contains(id)).collect(Collectors.toSet());
        Set<Long> toRemove = current.stream().filter(id -> !newTaxonIds.contains(id)).collect(Collectors.toSet());

        log.info("Taxon assignments replace: entityType={}, entityId={}, added={}, removed={}",
                entityType, entityId, toAdd.size(), toRemove.size());

        for (Long taxonId : toRemove) {
            assignmentRepository.unassign(entityType.name(), entityId, taxonId);
            auditHook.ifAvailable(h -> h.onAssignmentChanged(entityType, entityId, taxonId, AssignmentChange.UNASSIGNED));
        }
        for (Long taxonId : toAdd) {
            assignmentRepository.assign(entityType.name(), entityId, taxonId, actorId);
            auditHook.ifAvailable(h -> h.onAssignmentChanged(entityType, entityId, taxonId, AssignmentChange.ASSIGNED));
        }
    }

    public List<TaxonAssignment> getForEntity(@NonNull EntityType entityType, @NonNull Long entityId) {
        return assignmentRepository.findAllByEntity(entityType.name(), entityId);
    }

    public Map<Long, List<TaxonAssignment>> getForEntities(@NonNull EntityType entityType,
                                                            @NonNull Set<Long> entityIds) {
        return assignmentRepository.findAllByEntities(entityType.name(), entityIds)
                .stream()
                .collect(Collectors.groupingBy(TaxonAssignment::getEntityId));
    }

    public Set<Long> findEntityIdsByTaxonIds(@NonNull EntityType entityType, @NonNull Set<Long> taxonIds) {
        return assignmentRepository.findEntityIdsByTaxonIds(entityType.name(), taxonIds);
    }

    public Map<Long, Long> countByTaxonIds(@NonNull Set<Long> taxonIds) {
        return assignmentRepository.countByTaxonIds(taxonIds);
    }
}
