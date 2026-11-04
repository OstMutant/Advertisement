package org.ost.restapi.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.orchestrator.services.ContactAccessService;
import org.ost.orchestrator.services.ContactRevealService;
import org.ost.platform.contact.dto.ContactViewCountDto;
import org.ost.platform.contact.model.ContactChannel;
import org.ost.platform.core.model.EntityType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;

/** Contact channels of advertisements and provider profiles: which are set (public), revealing one (public, rate-limited, recorded) and this month's view counts (owner, administrator, moderator). */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ContactApiController {

    private final ContactRevealService contactRevealService;
    private final ContactAccessService contactAccessService;

    @Operation(operationId = "getAdvertisementContact", summary = "List the contact channels set for an advertisement",
            description = "Public. Only which channels exist, never their values; an advertisement shows its owner's provider-profile contact. Empty when none is set.")
    @GetMapping("/advertisements/{id}/contact")
    public ChannelsResponse advertisementContact(@PathVariable @NonNull Long id) {
        return new ChannelsResponse(contactRevealService.availableChannels(EntityType.ADVERTISEMENT, id));
    }

    @Operation(operationId = "getProviderProfileContact", summary = "List the contact channels set for a provider profile",
            description = "Public. Only which channels exist, never their values. Empty when none is set.")
    @GetMapping("/provider-profiles/{id}/contact")
    public ChannelsResponse providerProfileContact(@PathVariable @NonNull Long id) {
        return new ChannelsResponse(contactRevealService.availableChannels(EntityType.PROVIDER_PROFILE, id));
    }

    @Operation(operationId = "revealAdvertisementContact", summary = "Reveal one contact channel of an advertisement",
            description = "Public, no authentication needed; a bearer key, when sent, identifies the viewer. Records the view and counts toward a limit of 30 reveals per 15 minutes per client address (and viewer). Telegram and Viber also return a link that opens the chat.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(value = """
            {
              "channel": "TELEGRAM"
            }""")))
    @ApiResponse(responseCode = "404", description = "The advertisement has no such channel")
    @ApiResponse(responseCode = "429", description = "Reveal limit reached")
    @PostMapping("/advertisements/{id}/contact/reveals")
    public ContactRevealService.RevealedContact revealAdvertisementContact(@AuthenticationPrincipal Long actorId, @PathVariable @NonNull Long id,
            @RequestBody @Valid RevealRequest request, HttpServletRequest httpRequest) {
        return reveal(EntityType.ADVERTISEMENT, id, request, actorId, httpRequest);
    }

    @Operation(operationId = "revealProviderProfileContact", summary = "Reveal one contact channel of a provider profile",
            description = "Public, no authentication needed; a bearer key, when sent, identifies the viewer. Records the view and counts toward a limit of 30 reveals per 15 minutes per client address (and viewer). Telegram and Viber also return a link that opens the chat.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(value = """
            {
              "channel": "PHONE"
            }""")))
    @ApiResponse(responseCode = "404", description = "The provider profile has no such channel")
    @ApiResponse(responseCode = "429", description = "Reveal limit reached")
    @PostMapping("/provider-profiles/{id}/contact/reveals")
    public ContactRevealService.RevealedContact revealProviderProfileContact(@AuthenticationPrincipal Long actorId, @PathVariable @NonNull Long id,
            @RequestBody @Valid RevealRequest request, HttpServletRequest httpRequest) {
        return reveal(EntityType.PROVIDER_PROFILE, id, request, actorId, httpRequest);
    }

    @Operation(operationId = "getProviderProfileContactViews", summary = "This month's contact reveal counts of a provider profile",
            description = "The profile's owner, administrators and moderators only; one count per channel that was revealed this month.")
    @GetMapping("/provider-profiles/{id}/contact/views")
    @SecurityRequirement(name = "bearerKey")
    public List<ContactViewCountDto> providerProfileContactViews(@AuthenticationPrincipal @NonNull Long actorId,
            @PathVariable @NonNull Long id) {
        return contactAccessService.countProfileViewsThisMonth(actorId, id);
    }

    private ContactRevealService.RevealedContact reveal(EntityType entityType, Long id, RevealRequest request, Long actorId, HttpServletRequest httpRequest) {
        return contactRevealService.reveal(entityType, id, request.channel(), actorId, httpRequest.getRemoteAddr())
                .orElseThrow(() -> new NoSuchElementException("No " + request.channel() + " contact"));
    }

    /** The channels that are set, without their values. */
    public record ChannelsResponse(List<ContactChannel> channels) {
    }

    /** Which channel to reveal. */
    public record RevealRequest(@NotNull ContactChannel channel) {
    }
}
