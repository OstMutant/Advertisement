package org.ost.marketplace.ui.query.sort;

import org.junit.jupiter.api.Test;
import org.ost.marketplace.services.i18n.I18nKey;
import org.ost.marketplace.ui.query.elements.SortIcon;
import org.ost.marketplace.ui.query.elements.action.QueryActionBlockHandler;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** The sort line of a list page names the fields in the order of the processor's {@code Sort}, not in the order the fields were registered. */
class SortProcessorTest {

    private static final SortFieldMeta CREATED = SortFieldMeta.of("createdAt", I18nKey.USER_SORT_CREATED);
    private static final SortFieldMeta UPDATED = SortFieldMeta.of("updatedAt", I18nKey.PROVIDERS_SORT_UPDATED_AT);

    private final QueryActionBlockHandler handler = mock(QueryActionBlockHandler.class);

    @Test
    void descriptions_followTheOrderOfTheSort_notTheOrderOfRegistration() {
        SortProcessor processor = new SortProcessor(new CustomSort(Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("createdAt"))));
        processor.register(CREATED, mock(SortIcon.class), handler);
        processor.register(UPDATED, mock(SortIcon.class), handler);

        assertThat(describe(processor)).containsExactly("PROVIDERS_SORT_UPDATED_AT_DESC", "USER_SORT_CREATED_DESC");
    }

    @Test
    void descriptions_afterADirectionChange_theChangedFieldMovesToTheEnd() {
        SortProcessor processor = new SortProcessor(new CustomSort(Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("createdAt"))));
        processor.register(CREATED, mock(SortIcon.class), handler);
        processor.register(UPDATED, mock(SortIcon.class), handler);

        processor.getNewSort().updateSort("updatedAt", Direction.ASC);

        assertThat(describe(processor)).containsExactly("USER_SORT_CREATED_DESC", "PROVIDERS_SORT_UPDATED_AT_ASC");
    }

    @Test
    void descriptions_aSortPropertyThatIsNotRegistered_isSkipped() {
        SortProcessor processor = new SortProcessor(new CustomSort(Sort.by(Sort.Order.desc("promoted"), Sort.Order.desc("updatedAt"))));
        processor.register(UPDATED, mock(SortIcon.class), handler);

        assertThat(describe(processor)).containsExactly("PROVIDERS_SORT_UPDATED_AT_DESC");
    }

    private static List<String> describe(SortProcessor processor) {
        return processor.loopSortDescriptions((key, direction) -> key.name() + "_" + direction.name());
    }
}
