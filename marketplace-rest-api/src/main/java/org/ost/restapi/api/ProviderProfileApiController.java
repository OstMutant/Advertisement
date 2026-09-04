package org.ost.restapi.api;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ost.orchestrator.services.ProviderProfileReadService;
import org.ost.orchestrator.services.ProviderProfileSaveService;
import org.ost.platform.providerprofile.dto.ProviderProfileDto;
import org.ost.platform.providerprofile.dto.ProviderProfileFilterDto;
import org.ost.platform.providerprofile.dto.ProviderProfileSaveDto;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Full CRUD over {@link ProviderProfileSaveService}/{@link ProviderProfileReadService} — v1 is
 * self-service only, {@code targetUserId} is always the caller's own id.
 */
@RestController
@RequestMapping("/api/provider-profiles")
@RequiredArgsConstructor
public class ProviderProfileApiController {

    private final ProviderProfileSaveService saveService;
    private final ProviderProfileReadService readService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "bearerKey")
    public ProviderProfileDto create(@AuthenticationPrincipal Long actorId, @RequestBody @Valid ProviderProfileSaveDto dto) {
        Long id = saveService.save(dto, actorId, actorId);
        return readService.findById(id).orElseThrow();
    }

    @GetMapping
    public List<ProviderProfileDto> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return readService.getFiltered(ProviderProfileFilterDto.empty(), page, size, Sort.unsorted());
    }

    @GetMapping("/{id}")
    public ProviderProfileDto getById(@PathVariable Long id) {
        return readService.findById(id).orElseThrow(NoSuchElementException::new);
    }

    @PutMapping("/{id}")
    @SecurityRequirement(name = "bearerKey")
    public ProviderProfileDto update(@AuthenticationPrincipal Long actorId, @PathVariable Long id, @RequestBody @Valid ProviderProfileSaveDto dto) {
        ProviderProfileSaveDto withId = new ProviderProfileSaveDto(id, dto.kind(), dto.about(), dto.categoryIds(), dto.cityTaxonId(), dto.version());
        Long savedId = saveService.save(withId, actorId, actorId);
        return readService.findById(savedId).orElseThrow();
    }

    @DeleteMapping("/{id}")
    @SecurityRequirement(name = "bearerKey")
    public void delete(@AuthenticationPrincipal Long actorId, @PathVariable Long id, @RequestParam(required = false) Long version) {
        saveService.delete(id, actorId, version);
    }
}
