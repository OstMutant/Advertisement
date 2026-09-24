package org.ost.marketplace.ui.views.main.tabs.referencedata.overlay.modes;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.data.binder.Setter;
import com.vaadin.flow.data.validator.StringLengthValidator;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.function.ValueProvider;
import org.ost.marketplace.ui.dto.EditDto;
import org.ost.marketplace.ui.views.components.fields.UiTextArea;
import org.ost.marketplace.ui.views.components.fields.UiTextField;
import org.ost.marketplace.ui.views.components.overlay.OverlayFormBinder;
import org.ost.platform.taxon.dto.TaxonSnapshotDto;
import org.ost.platform.taxon.dto.TaxonTranslationDto;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Shared EN/UK locale-form mechanics reused by {@code CategoryFormOverlayModeHandler} and
 * {@code CityFormOverlayModeHandler} -- composition, not inheritance, since the two handlers
 * differ in DTO type, save-path {@code TaxonType}, and audit/history wiring beyond what this
 * class covers.
 */
class LocaleTranslationForm<T extends EditDto> {

    // Nested records are implicitly static, so they cannot reference the enclosing class's own
    // T -- each declares its own, same pattern as OverlayFormBinder.Parameters<T>.
    record Field<T>(UiTextField nameField, UiTextArea descriptionField,
            ValueProvider<T, String> getName, Setter<T, String> setName,
            ValueProvider<T, String> getDescription, Setter<T, String> setDescription,
            ValueProvider<TaxonSnapshotDto, String> getSnapshotName, ValueProvider<TaxonSnapshotDto, String> getSnapshotDescription) {}

    record Accessors<T>(ValueProvider<T, String> getName, Setter<T, String> setName,
            ValueProvider<T, String> getDescription, Setter<T, String> setDescription,
            ValueProvider<TaxonSnapshotDto, String> getSnapshotName, ValueProvider<TaxonSnapshotDto, String> getSnapshotDescription) {}

    record Labels(String fieldName, String fieldNamePlaceholder,
            String fieldDescription, String fieldDescriptionPlaceholder,
            String validationNameRequired, String validationNameLength,
            String validationDescriptionRequired, String validationDescriptionLength,
            String localeTabEn, String localeTabUk) {}

    private final List<Field<T>> fields;
    private final Labels         labels;

    LocaleTranslationForm(String fieldNameTestId, String fieldDescriptionTestId, Labels labels,
                           Accessors<T> en, Accessors<T> uk) {
        this.labels = labels;
        UiTextField nameEn = new UiTextField(labels.fieldName(), labels.fieldNamePlaceholder(), 255, true, fieldNameTestId);
        UiTextArea  descEn = new UiTextArea(labels.fieldDescription(), labels.fieldDescriptionPlaceholder(), 2000, true, fieldDescriptionTestId);
        UiTextField nameUk = new UiTextField(labels.fieldName(), labels.fieldNamePlaceholder(), 255, true, fieldNameTestId);
        UiTextArea  descUk = new UiTextArea(labels.fieldDescription(), labels.fieldDescriptionPlaceholder(), 2000, true, fieldDescriptionTestId);
        this.fields = List.of(
                new Field<>(nameEn, descEn, en.getName(), en.setName(), en.getDescription(), en.setDescription(),
                        en.getSnapshotName(), en.getSnapshotDescription()),
                new Field<>(nameUk, descUk, uk.getName(), uk.setName(), uk.getDescription(), uk.setDescription(),
                        uk.getSnapshotName(), uk.getSnapshotDescription())
        );
    }

    void wireValueChangeListeners(Runnable onChange) {
        for (Field<T> f : fields) {
            f.nameField().setValueChangeMode(ValueChangeMode.EAGER);
            f.descriptionField().setValueChangeMode(ValueChangeMode.EAGER);
            f.nameField().addValueChangeListener(_ -> onChange.run());
            f.descriptionField().addValueChangeListener(_ -> onChange.run());
        }
    }

    void bindValidation(OverlayFormBinder<T> binder) {
        for (Field<T> f : fields) {
            binder.getBinder().forField(f.nameField())
                    .asRequired(labels.validationNameRequired())
                    .withValidator(new StringLengthValidator(labels.validationNameLength(), 1, 255))
                    .bind(f.getName(), f.setName());
            binder.getBinder().forField(f.descriptionField())
                    .asRequired(labels.validationDescriptionRequired())
                    .withValidator(new StringLengthValidator(labels.validationDescriptionLength(), 1, 2000))
                    .bind(f.getDescription(), f.setDescription());
        }
    }

    void copyLocaleFields(T src, T tgt) {
        for (Field<T> f : fields) {
            f.setName().accept(tgt, f.getName().apply(src));
            f.setDescription().accept(tgt, f.getDescription().apply(src));
        }
    }

    void restoreFromSnapshot(TaxonSnapshotDto snapshot, T dto) {
        for (Field<T> f : fields) {
            f.setName().accept(dto, f.getSnapshotName().apply(snapshot));
            f.setDescription().accept(dto, f.getSnapshotDescription().apply(snapshot));
        }
    }

    Map<Locale, TaxonTranslationDto> extractTranslations(T dto) {
        Field<T> en = fields.get(0);
        Field<T> uk = fields.get(1);
        return Map.of(
                Locale.ENGLISH, TaxonTranslationDto.builder().locale("en")
                        .name(en.getName().apply(dto)).description(en.getDescription().apply(dto)).build(),
                Locale.forLanguageTag("uk"), TaxonTranslationDto.builder().locale("uk")
                        .name(uk.getName().apply(dto)).description(uk.getDescription().apply(dto)).build()
        );
    }

    void applyTranslations(T dto, List<TaxonTranslationDto> translations) {
        for (TaxonTranslationDto t : translations) {
            if ("en".equals(t.getLocale())) {
                fields.get(0).setName().accept(dto, t.getName());
                fields.get(0).setDescription().accept(dto, t.getDescription());
            } else if ("uk".equals(t.getLocale())) {
                fields.get(1).setName().accept(dto, t.getName());
                fields.get(1).setDescription().accept(dto, t.getDescription());
            }
        }
    }

    Div buildFieldsCard(Component sectionIcon, String sectionLabel) {
        Div cardHeader = new Div(sectionIcon, new Span(sectionLabel));
        cardHeader.addClassName("overlay__form-card-header");

        H4 enLabel = new H4(labels.localeTabEn());
        enLabel.addClassName("taxon-locale-label");
        Div enContent = new Div(fields.get(0).nameField(), fields.get(0).descriptionField());
        enContent.addClassName("taxon-locale-content");

        H4 ukLabel = new H4(labels.localeTabUk());
        ukLabel.addClassName("taxon-locale-label");
        Div ukContent = new Div(fields.get(1).nameField(), fields.get(1).descriptionField());
        ukContent.addClassName("taxon-locale-content");

        Div fieldsCard = new Div(cardHeader, enLabel, enContent, ukLabel, ukContent);
        fieldsCard.addClassName("overlay__form-fields-card");
        return fieldsCard;
    }
}
