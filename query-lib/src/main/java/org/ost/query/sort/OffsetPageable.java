package org.ost.query.sort;

import lombok.NonNull;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * A {@link Pageable} carrying an arbitrary row offset, not one derived from {@code page * size}.
 * {@link org.springframework.data.domain.PageRequest} always computes {@link #getOffset()} as
 * {@code page * size} — unusable for a caller (e.g. Vaadin's {@code CallbackDataProvider}) that
 * already has a raw, possibly non-page-aligned offset and would otherwise have to
 * (incorrectly) round-trip it through a page number via integer division.
 */
public record OffsetPageable(long offset, int limit, @NonNull Sort sort) implements Pageable {

    @Override
    public int getPageNumber() {
        return limit == 0 ? 0 : (int) (offset / limit);
    }

    @Override
    public int getPageSize() {
        return limit;
    }

    @Override
    public long getOffset() {
        return offset;
    }

    @Override
    public Sort getSort() {
        return sort;
    }

    @Override
    public Pageable next() {
        return new OffsetPageable(offset + limit, limit, sort);
    }

    @Override
    public Pageable previousOrFirst() {
        return hasPrevious() ? new OffsetPageable(Math.max(0, offset - limit), limit, sort) : first();
    }

    @Override
    public Pageable first() {
        return new OffsetPageable(0, limit, sort);
    }

    @Override
    public Pageable withPage(int pageNumber) {
        return new OffsetPageable((long) pageNumber * limit, limit, sort);
    }

    @Override
    public boolean hasPrevious() {
        return offset > 0;
    }
}
