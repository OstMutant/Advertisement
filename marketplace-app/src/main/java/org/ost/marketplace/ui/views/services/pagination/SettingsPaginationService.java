package org.ost.marketplace.ui.views.services.pagination;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.marketplace.services.auth.AuthContextService;
import org.ost.marketplace.ui.views.components.PaginationBar;
import org.ost.platform.user.dto.UserSettingsDto;
import org.ost.platform.user.spi.UserPort;
import org.ost.platform.user.spi.UserSettingsChangedHook;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.ToIntFunction;

@Component
@RequiredArgsConstructor
public class SettingsPaginationService implements UserSettingsChangedHook {

    private final AuthContextService  authContextService;
    private final UserPort            userPort;

    private final List<BindingEntry> entries = new CopyOnWriteArrayList<>();

    record BindingEntry(Long userId, PaginationBar bar, ToIntFunction<UserSettingsDto> extractor, Runnable refresh) {}

    public void register(@NonNull PaginationBar bar, @NonNull ToIntFunction<UserSettingsDto> extractor, @NonNull Runnable refresh) {
        authContextService.getCurrentUser().ifPresent(user -> {
            bar.setPageSize(extractor.applyAsInt(userPort.loadSettings(user.id())));
            entries.add(new BindingEntry(user.id(), bar, extractor, refresh));
            bar.addDetachListener(_ -> unregister(bar));
        });
    }

    public void unregister(@NonNull PaginationBar bar) {
        entries.removeIf(e -> e.bar() == bar);
    }

    @Override
    public void onSettingsChanged(@NonNull Long userId, @NonNull UserSettingsDto settings) {
        entries.stream()
                .filter(e -> e.userId().equals(userId))
                .forEach(e -> e.bar().getUI().ifPresent(ui -> ui.access(() -> {
                    e.bar().setPageSize(e.extractor().applyAsInt(settings));
                    e.refresh().run();
                })));
    }
}
