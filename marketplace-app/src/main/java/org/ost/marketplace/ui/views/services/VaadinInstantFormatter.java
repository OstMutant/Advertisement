package org.ost.marketplace.ui.views.services;

import org.ost.platform.core.i18n.InstantFormatter;
import org.ost.marketplace.ui.views.utils.TimeZoneUtil;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class VaadinInstantFormatter implements InstantFormatter {

    @Override
    public String formatInstantHuman(Instant instant) {
        return TimeZoneUtil.formatInstantHuman(instant);
    }

    @Override
    public String formatInstant(Instant instant) {
        return TimeZoneUtil.formatInstant(instant);
    }
}
