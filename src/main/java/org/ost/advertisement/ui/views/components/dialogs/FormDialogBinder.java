package org.ost.advertisement.ui.views.components.dialogs;

import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.ost.advertisement.ui.dto.EditDto;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

@Slf4j
@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class FormDialogBinder<T extends EditDto> {

    @Value
    @lombok.Builder
    public static class Config<T> {
        @NonNull Class<T> clazz;
        @NonNull T        dto;
    }

    private Config<T> config;

    @Getter
    private Binder<T> binder;

    public T getDto() {
        return config.getDto();
    }

    private FormDialogBinder<T> setup(Config<T> config) {
        this.config = config;
        this.binder = new Binder<>(config.getClazz());
        return this;
    }

    public void readInitialValues() {
        binder.readBean(config.getDto());
    }

    public boolean hasChanges() {
        return binder.hasChanges();
    }

    public boolean save(Saver<T> saver) {
        T dto = config.getDto();
        if (binder.writeBeanIfValid(dto)) {
            saver.save(dto);
            return true;
        } else {
            return false;
        }
    }

    @FunctionalInterface
    public interface Saver<T> {
        void save(T dto);
    }

    @SpringComponent
    @Scope("prototype")
    @RequiredArgsConstructor
    public static class Builder<T extends EditDto> {
        private final ObjectProvider<FormDialogBinder<T>> provider;

        public FormDialogBinder<T> build(FormDialogBinder.Config<T> config) {
            return provider.getObject().setup(config);
        }
    }
}