package org.ost.restapi.api;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.orchestrator.services.ApiKeyManagementService;
import org.ost.platform.apikey.dto.ApiKeySummaryDto;
import org.ost.platform.user.spi.AuthenticatedPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Issues, lists, and revokes the caller's own API keys. Issuance is authenticated via HTTP Basic
 * (the caller has no key yet); listing and revocation use the same bearer-key authentication as
 * every other {@code /api/**} endpoint.
 */
@RestController
@RequestMapping("/api/api-keys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyManagementService apiKeyManagementService;

    @PostMapping
    @SecurityRequirement(name = "basicAuth")
    public ApiKeyCreatedResponse create(@AuthenticationPrincipal @NonNull AuthenticatedPrincipal principal,
            @RequestBody(required = false) ApiKeyCreateRequest request) {
        String label = request != null ? request.label() : null;
        String rawKey = apiKeyManagementService.create(principal.toUserDto().id(), label);
        return new ApiKeyCreatedResponse(rawKey);
    }

    @GetMapping
    @SecurityRequirement(name = "bearerKey")
    public List<ApiKeySummaryDto> list(@AuthenticationPrincipal @NonNull Long actorId) {
        return apiKeyManagementService.listForActor(actorId);
    }

    @DeleteMapping("/{id}")
    @SecurityRequirement(name = "bearerKey")
    public void revoke(@AuthenticationPrincipal @NonNull Long actorId, @PathVariable @NonNull Long id) {
        apiKeyManagementService.revoke(actorId, id);
    }

    /** Request body for issuing a new API key. */
    public record ApiKeyCreateRequest(String label) {
    }

    /** The raw API key, returned only once at issuance time — never retrievable again afterward. */
    public record ApiKeyCreatedResponse(String rawKey) {
    }
}
