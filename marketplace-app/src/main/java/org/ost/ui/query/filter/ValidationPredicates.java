package org.ost.ui.query.filter;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.ost.ui.query.filter.ValidationService;

import java.util.function.BiPredicate;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ValidationPredicates {

    /**
     * Range predicate that uses isValidForProperty — catches both field-level
     * constraints AND class-level @ValidRange violations mapped to the property.
     */
    public static <F> BiPredicate<ValidationService<F>, F> range(String start, String end) {
        return (v, dto) -> v.isValidForProperty(dto, start) && v.isValidForProperty(dto, end);
    }
}