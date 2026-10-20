package org.ost.orchestrator.services;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.contact.model.ContactChannel;
import org.ost.platform.core.FailureRateLimiter;
import org.ost.platform.core.model.EntityType;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The contact-reveal use case shared by the Vaadin UI and the REST API: rate limit, channel lookup, view recording and the deep link, in one place. */
@Service
@RequiredArgsConstructor
public class ContactRevealService {

    private static final int MAX_REVEALS = 30;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final FailureRateLimiter limiter = new FailureRateLimiter(MAX_REVEALS, WINDOW);
    private final ContactAccessService contactAccessService;

    /** The value of one revealed channel and, for messengers, the link that opens the chat; {@code deepLink} is null for a phone. */
    public record RevealedContact(ContactChannel channel, String value, String deepLink) {
    }

    /** The channels that are set for the entity (an advertisement without its own contact falls back to its owner's profile), without their values. */
    public List<ContactChannel> availableChannels(@NonNull EntityType entityType, @NonNull Long entityId) {
        return contactAccessService.resolveContact(entityType, entityId)
                .map(contact -> contact.presentChannels().stream().map(Map.Entry::getKey).toList())
                .orElse(List.of());
    }

    /**
     * Counts one reveal attempt against the limit of the client address (and the viewer, when signed in),
     * then returns the channel's value and deep link and records the view; empty when the entity has no such channel.
     *
     * @throws org.ost.platform.core.TooManyAttemptsException once the limit is reached
     */
    public Optional<RevealedContact> reveal(@NonNull EntityType entityType, @NonNull Long entityId,
            @NonNull ContactChannel channel, Long viewerId, @NonNull String clientAddress) {
        String key = viewerId != null ? clientAddress + "|" + viewerId : clientAddress;
        limiter.checkAllowed(key, "Too many contact reveals, try again later");
        limiter.recordFailure(key);

        Optional<RevealedContact> revealed = contactAccessService.resolveContact(entityType, entityId)
                .flatMap(contact -> contact.presentChannels().stream().filter(entry -> entry.getKey() == channel).findFirst())
                .map(entry -> new RevealedContact(channel, entry.getValue(), deepLink(channel, entry.getValue())));
        revealed.ifPresent(contact -> contactAccessService.recordView(entityType, entityId, channel, viewerId, contact.value()));
        return revealed;
    }

    private static String deepLink(ContactChannel channel, String value) {
        return switch (channel) {
            case PHONE -> null;
            case TELEGRAM -> "https://t.me/" + value;
            case VIBER -> "viber://chat?number=" + URLEncoder.encode(value, StandardCharsets.UTF_8);
        };
    }
}
