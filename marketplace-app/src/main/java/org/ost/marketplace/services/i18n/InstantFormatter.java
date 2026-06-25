package org.ost.marketplace.services.i18n;

import java.time.Instant;

public interface InstantFormatter {
    String formatInstantHuman(Instant instant);
}
