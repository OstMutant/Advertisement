package org.ost.query.config;

import jakarta.validation.Validator;
import org.ost.platform.core.ComponentFactory;
import org.ost.query.ui.elements.SortIcon;
import org.ost.query.ui.elements.SvgIcon;
import org.ost.query.ui.elements.fields.QueryDateTimeField;
import org.ost.query.ui.elements.fields.QueryMultiSelectComboField;
import org.ost.query.ui.elements.fields.QueryNumberField;
import org.ost.query.ui.elements.fields.QueryTextField;
import org.ost.query.ui.elements.rows.QueryInlineRow;
import org.ost.query.ui.filter.ValidationService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

@AutoConfiguration
@ComponentScan("org.ost.query.ui")
@ConditionalOnClass(Validator.class)
public class QueryAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ValidationService<?> validationService(Validator validator) {
        return new ValidationService<>(validator);
    }

    @Bean @ConditionalOnMissingBean
    public ComponentFactory<QueryTextField> queryTextFieldFactory(ObjectProvider<QueryTextField> p) {
        return new ComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public ComponentFactory<QueryDateTimeField> queryDateTimeFieldFactory(ObjectProvider<QueryDateTimeField> p) {
        return new ComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public ComponentFactory<QueryNumberField> queryNumberFieldFactory(ObjectProvider<QueryNumberField> p) {
        return new ComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    @SuppressWarnings("rawtypes")
    public ComponentFactory<QueryMultiSelectComboField> queryMultiSelectComboFieldFactory(ObjectProvider<QueryMultiSelectComboField> p) {
        return new ComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public ComponentFactory<QueryInlineRow> queryInlineRowFactory(ObjectProvider<QueryInlineRow> p) {
        return new ComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public ComponentFactory<SortIcon> sortIconFactory(ObjectProvider<SortIcon> p) {
        return new ComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public ComponentFactory<SvgIcon> svgIconFactory(ObjectProvider<SvgIcon> p) {
        return new ComponentFactory<>(p);
    }
}
