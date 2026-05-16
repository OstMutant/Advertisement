package org.ost.advertisement.core.i18n;

import java.time.Instant;

public interface InstantFormatter {
    String formatInstantHuman(Instant instant);
    String formatInstant(Instant instant);
}
