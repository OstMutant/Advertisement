package org.ost.marketplace.ui.views.services;

import org.ost.platform.core.i18n.InstantFormatter;
import org.ost.query.ui.utils.TimeZoneUtil;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class VaadinInstantFormatter implements InstantFormatter {

    @Override
    public String formatInstantHuman(Instant instant) {
        return TimeZoneUtil.formatInstantHuman(instant);
    }

}
