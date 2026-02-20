package org.ost.advertisement.ui.views.components.dialogs;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.*;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.utils.NotificationType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

@Slf4j
@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class FormDialogDelegate<T> {

    @Value
    @lombok.Builder
    public static class Config<T> {
        @NonNull Class<T> clazz;
        @NonNull T dto;
        Runnable refresh;
    }

    private final I18nService i18n;
    private final DialogLayout layout = new DialogLayout();
    private final Dialog dialog = new Dialog();

    @Getter
    private Config<T> config;

    @Getter
    private Binder<T> binder;

    private FormDialogDelegate<T> setupDialog(Config<T> config) {
        this.config = config;
        this.binder = new Binder<>(config.getClazz());
        this.binder.setBean(config.getDto());

        DialogStyle.apply(dialog, "");
        dialog.add(layout.getLayout());

        Runnable refresh = config.getRefresh();
        if (refresh != null) {
            dialog.addOpenedChangeListener(event -> {
                if (!event.isOpened()) refresh.run();
            });
        }
        return this;
    }

    public void setTitle(String header) {
        dialog.setHeaderTitle(header);
    }

    public void addDialogThemeName(String themeName) {
        dialog.addThemeName(themeName);
    }

    public void addContent(Component... components) {
        layout.addFormContent(components);
    }

    public void addActions(Component... components) {
        layout.addActions(components);
    }

    public void save(Saver<T> saver, I18nKey successKey, I18nKey errorKey) {
        T dto = config.getDto();
        if (binder.writeBeanIfValid(dto)) {
            saver.save(dto);
            NotificationType.SUCCESS.show(i18n.get(successKey));
            dialog.close();
        } else {
            NotificationType.ERROR.show(i18n.get(errorKey, "Validation failed"));
        }
    }

    public void open() {
        dialog.open();
    }

    public void close() {
        dialog.close();
    }

    @FunctionalInterface
    public interface Saver<T> {
        void save(T dto);
    }

    @SpringComponent
    @RequiredArgsConstructor
    public static class Builder<T> {
        private final ObjectProvider<FormDialogDelegate<T>> provider;

        public FormDialogDelegate<T> build(FormDialogDelegate.Config<T> config) {
            return provider.getObject().setupDialog(config);
        }
    }

}