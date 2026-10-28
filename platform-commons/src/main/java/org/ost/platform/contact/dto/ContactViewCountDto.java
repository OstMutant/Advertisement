package org.ost.platform.contact.dto;

import lombok.NonNull;
import org.ost.platform.contact.model.ContactChannel;

/** Reveal count for one channel over the current calendar month. */
public record ContactViewCountDto(@NonNull ContactChannel channel, long count) {
}
