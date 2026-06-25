package org.ost.marketplace.ui.views.services.pagination;

import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.marketplace.ui.views.components.PaginationBar;
import org.ost.platform.user.dto.UserSettingsDto;
import org.springframework.context.annotation.Scope;

import java.util.function.ToIntFunction;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class SettingsPaginationBinding {

    private final SettingsPaginationService support;

    private PaginationBar bar;

    public void register(@NonNull PaginationBar bar, @NonNull ToIntFunction<UserSettingsDto> extractor, @NonNull Runnable refresh) {
        this.bar = bar;
        support.register(bar, extractor, refresh);
    }

    public void unregister() {
        if (bar != null) support.unregister(bar);
    }
}
