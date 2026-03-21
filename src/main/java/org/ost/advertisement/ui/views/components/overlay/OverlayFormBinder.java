package org.ost.advertisement.ui.views.components.overlay;

import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.ost.advertisement.ui.dto.EditDto;
import org.ost.advertisement.ui.views.rules.Configurable;
import org.ost.advertisement.ui.views.rules.ComponentBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

@Slf4j
@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class OverlayFormBinder<T extends EditDto>
        implements Configurable<OverlayFormBinder<T>, OverlayFormBinder.Parameters<T>> {

    @Value
    @lombok.Builder
    public static class Parameters<T> {
        @NonNull Class<T> clazz;
        @NonNull T        dto;
    }

    @FunctionalInterface
    public interface Saver<T> {
        void save(T dto);
    }

    @SpringComponent
    @Scope("prototype")
    @RequiredArgsConstructor
    public static class Builder<T extends EditDto>
            extends ComponentBuilder<OverlayFormBinder<T>, Parameters<T>> {
        @Getter
        private final ObjectProvider<OverlayFormBinder<T>> provider;
    }

    private Parameters<T> params;

    @Getter
    private Binder<T> binder;

    @Override
    public OverlayFormBinder<T> configure(Parameters<T> p) {
        this.params = p;
        this.binder = new Binder<>(p.getClazz());
        return this;
    }

    public T getDto() {
        return params.getDto();
    }

    public void readInitialValues() {
        binder.readBean(params.getDto());
    }

    public boolean hasChanges() {
        return binder.hasChanges();
    }

    public boolean save(Saver<T> saver) {
        T dto = params.getDto();
        if (binder.writeBeanIfValid(dto)) {
            saver.save(dto);
            return true;
        } else {
            return false;
        }
    }
}
