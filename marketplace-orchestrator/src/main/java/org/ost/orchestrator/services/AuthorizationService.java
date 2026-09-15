package org.ost.orchestrator.services;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.user.dto.UserDto;
import org.ost.platform.user.spi.UserAuthorizationPort;
import org.springframework.stereotype.Service;

/**
 * Application-level role and ownership checks, reused by marketplace-app's AccessEvaluator and by
 * this module's own save/delete services (service-boundary authorization -- see
 * {@code marketplace-orchestrator/CLAUDE.md}). Mandatory {@link UserAuthorizationPort} dependency,
 * same shape as {@link UserDeleteService}'s {@code UserAccountPort} field --
 * user-spring-boot-starter is a compile-scope, non-optional dependency of the final app, so this
 * isn't wrapped in {@code ComponentFactory}.
 */
@Service
@RequiredArgsConstructor
public class AuthorizationService {

    private final UserAuthorizationPort authorizationPort;
    private final ActorLookupService    actorLookupService;

    public boolean isAdmin(@NonNull UserDto user) {
        return authorizationPort.isAdmin(user);
    }

    public boolean isModerator(@NonNull UserDto user) {
        return authorizationPort.isModerator(user);
    }

    public boolean isOwner(@NonNull UserDto user, @NonNull Long ownerId) {
        return authorizationPort.isOwner(user, ownerId);
    }

    // ── Rule composition (UserDto in hand -- the single source of truth for each rule) ────────

    /** True if actor is admin, moderator, or owns ownerId. ownerId == null reduces this to
     *  "admin or moderator" (no-ownership-concept domains, e.g. Taxon). */
    public boolean canOperate(@NonNull UserDto actor, Long ownerId) {
        return isAdmin(actor) || isModerator(actor) || (ownerId != null && isOwner(actor, ownerId));
    }

    /** Stricter than {@link #canOperate}: admin or self only, never moderator -- matches
     *  marketplace-app's {@code AccessEvaluator.canEditUserAccount} (a moderator gets read-only on
     *  accounts, never edit rights). */
    public boolean canEditAccount(@NonNull UserDto actor, @NonNull Long targetUserId) {
        return isAdmin(actor) || actor.id().equals(targetUserId);
    }

    /** Stricter still than {@link #canEditAccount}: admin editing someone *else's* account only --
     *  never self, even an admin's own role -- matches marketplace-app's
     *  {@code AccessEvaluator.canEditRole}. */
    public boolean canEditRole(@NonNull UserDto actor, @NonNull Long targetUserId) {
        return isAdmin(actor) && !actor.id().equals(targetUserId);
    }

    // ── Service-boundary checks (id-only callers, no UserDto in hand -- resolve, then delegate above) ─

    public boolean canOperate(@NonNull Long actingUserId, Long ownerId) {
        return actorLookupService.findById(actingUserId).map(u -> canOperate(u, ownerId)).orElse(false);
    }

    public boolean canEditAccount(@NonNull Long actingUserId, @NonNull Long targetUserId) {
        return actorLookupService.findById(actingUserId).map(u -> canEditAccount(u, targetUserId)).orElse(false);
    }

    public boolean canEditRole(@NonNull Long actingUserId, @NonNull Long targetUserId) {
        return actorLookupService.findById(actingUserId).map(u -> canEditRole(u, targetUserId)).orElse(false);
    }

    /** Alias for {@code canOperate(actingUserId, null)} -- self-documents the no-ownership-concept
     *  case at call sites (e.g. Taxon/ProviderProfile's {@code kind == SUPPORT} rule). */
    public boolean isPrivileged(@NonNull Long actingUserId) {
        return canOperate(actingUserId, null);
    }

    public void requireCanOperate(@NonNull Long actingUserId, Long ownerId) {
        if (!canOperate(actingUserId, ownerId)) {
            throw new AccessDeniedException("User " + actingUserId + " may not operate on resource owned by " + ownerId);
        }
    }

    /** Throwing alias for {@code requireCanOperate(actingUserId, null)} -- self-documents the
     *  no-ownership-concept case at call sites (e.g. Taxon's category/city admin writes). */
    public void requireIsPrivileged(@NonNull Long actingUserId) {
        requireCanOperate(actingUserId, null);
    }

    public void requireCanEditAccount(@NonNull Long actingUserId, @NonNull Long targetUserId) {
        if (!canEditAccount(actingUserId, targetUserId)) {
            throw new AccessDeniedException("User " + actingUserId + " may not edit account " + targetUserId);
        }
    }

    public void requireCanEditRole(@NonNull Long actingUserId, @NonNull Long targetUserId) {
        if (!canEditRole(actingUserId, targetUserId)) {
            throw new AccessDeniedException("User " + actingUserId + " may not change the role of account " + targetUserId);
        }
    }

    /** UserDto-taking variant for callers that already resolved the actor (e.g. self-edit, where
     *  the actor's own row is also the target row) -- avoids a redundant id-based lookup. */
    public void requireCanEditAccount(@NonNull UserDto actor, @NonNull Long targetUserId) {
        if (!canEditAccount(actor, targetUserId)) {
            throw new AccessDeniedException("User " + actor.id() + " may not edit account " + targetUserId);
        }
    }

    public void requireCanEditRole(@NonNull UserDto actor, @NonNull Long targetUserId) {
        if (!canEditRole(actor, targetUserId)) {
            throw new AccessDeniedException("User " + actor.id() + " may not change the role of account " + targetUserId);
        }
    }
}
