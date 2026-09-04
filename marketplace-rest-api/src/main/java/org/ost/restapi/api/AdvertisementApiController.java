package org.ost.restapi.api;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ost.orchestrator.services.AdvertisementReadService;
import org.ost.orchestrator.services.AdvertisementSaveService;
import org.ost.platform.advertisement.dto.AdvertisementFilterDto;
import org.ost.platform.advertisement.dto.AdvertisementInfoDto;
import org.ost.platform.advertisement.dto.AdvertisementSaveDto;
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
 * Full CRUD over {@link AdvertisementSaveService}/{@link AdvertisementReadService} — reads are
 * public, writes need bearer authentication. No photo upload via this API (no in-progress gallery
 * to commit, unlike the Vaadin form).
 */
@RestController
@RequestMapping("/api/advertisements")
@RequiredArgsConstructor
public class AdvertisementApiController {

    private final AdvertisementSaveService saveService;
    private final AdvertisementReadService readService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "bearerKey")
    public AdvertisementInfoDto create(@AuthenticationPrincipal Long actorId, @RequestBody @Valid AdvertisementSaveDto dto) {
        Long id = saveService.save(dto, actorId, ref -> null);
        return readService.findById(id).orElseThrow();
    }

    @GetMapping
    public List<AdvertisementInfoDto> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return readService.getFiltered(AdvertisementFilterDto.empty(), page, size, Sort.unsorted());
    }

    @GetMapping("/{id}")
    public AdvertisementInfoDto getById(@PathVariable Long id) {
        return readService.findById(id).orElseThrow(NoSuchElementException::new);
    }

    @PutMapping("/{id}")
    @SecurityRequirement(name = "bearerKey")
    public AdvertisementInfoDto update(@AuthenticationPrincipal Long actorId, @PathVariable Long id, @RequestBody @Valid AdvertisementSaveDto dto) {
        AdvertisementSaveDto withId = new AdvertisementSaveDto(id, dto.title(), dto.description(), dto.adKind(), dto.categoryIds(), dto.cityTaxonId(), dto.version());
        Long savedId = saveService.save(withId, actorId, ref -> null);
        return readService.findById(savedId).orElseThrow();
    }

    @DeleteMapping("/{id}")
    @SecurityRequirement(name = "bearerKey")
    public void delete(@AuthenticationPrincipal Long actorId, @PathVariable Long id, @RequestParam(required = false) Long version) {
        saveService.delete(id, actorId, version);
    }
}
