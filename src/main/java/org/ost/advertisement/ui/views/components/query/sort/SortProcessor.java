package org.ost.advertisement.ui.views.components.query.sort;

import lombok.Getter;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.dto.sort.CustomSort;
import org.ost.advertisement.ui.views.components.query.action.QueryActionBlockHandler;
import org.ost.advertisement.ui.views.components.query.sort.SortIcon.SortHighlightColor;
import org.ost.advertisement.ui.views.components.query.sort.meta.SortFieldMeta;
import org.springframework.data.domain.Sort.Direction;

import java.util.*;
import java.util.function.BiFunction;

public class SortProcessor {

    protected final CustomSort defaultSort;
    @Getter
    protected final CustomSort originalSort;
    @Getter
    protected final CustomSort newSort;

    private final Map<SortFieldMeta, SortIcon> fieldsMap = new LinkedHashMap<>();

    public SortProcessor(CustomSort defaultSort) {
        this.defaultSort = defaultSort;
        this.originalSort = defaultSort.copy();
        this.newSort = defaultSort.copy();
    }

    public void register(SortFieldMeta meta, SortIcon sortIcon, QueryActionBlockHandler queryActionBlockHandler) {
        fieldsMap.put(meta, sortIcon);
        String property = meta.property();
        sortIcon.setDirection(newSort.getDirection(property));
        sortIcon.addDirectionChangedListener(e -> {
            newSort.updateSort(property, e.getDirection());
            queryActionBlockHandler.updateDirtyState(isSortingChanged());
            sortIcon.setColor(refreshColor(property));
        });
        sortIcon.setColor(refreshColor(property));
    }

    public void refreshItemsColor() {
        for (Map.Entry<SortFieldMeta, SortIcon> entry : fieldsMap.entrySet()) {
            refreshItemColor(entry.getKey().property(), entry.getValue());
        }
    }

    public boolean isSortingChanged() {
        return !originalSort.areSortsEquivalent(newSort);
    }

    public void updateSorting() {
        originalSort.copyFrom(newSort);
    }

    public void clearSorting() {
        originalSort.copyFrom(defaultSort);
        newSort.copyFrom(defaultSort);

        for (Map.Entry<SortFieldMeta, SortIcon> entry : fieldsMap.entrySet()) {
            updateItemDirectionAndRefreshColor(entry.getKey().property(), entry.getValue());
        }
    }

    public List<String> loopSortDescriptions(BiFunction<I18nKey, Direction, String> transformer) {
        List<String> result = new ArrayList<>();
        for (Map.Entry<SortFieldMeta, SortIcon> entry : fieldsMap.entrySet()) {
            SortFieldMeta meta = entry.getKey();
            Direction direction = newSort.getDirection(meta.property());
            if (direction != null) {
                result.add(transformer.apply(meta.i18nKey(), direction));
            }
        }
        return result;
    }

    private void updateItemDirectionAndRefreshColor(String property, SortIcon sortIcon) {
        sortIcon.setDirection(newSort.getDirection(property));
        refreshItemColor(property, sortIcon);
    }

    private void refreshItemColor(String property, SortIcon sortIcon) {
        sortIcon.setColor(refreshColor(property));
    }

    private SortHighlightColor refreshColor(String property) {
        Direction newVal = newSort.getDirection(property);
        Direction origVal = originalSort.getDirection(property);
        Direction defVal = defaultSort.getDirection(property);

        if (Objects.equals(newVal, origVal)) {
            return Objects.equals(origVal, defVal) ? SortHighlightColor.DEFAULT : SortHighlightColor.CUSTOM;
        }
        return SortHighlightColor.CHANGED;
    }
}

