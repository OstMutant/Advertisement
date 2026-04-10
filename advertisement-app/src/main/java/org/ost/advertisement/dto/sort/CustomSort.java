package org.ost.advertisement.dto.sort;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.domain.Sort.Order;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@AllArgsConstructor
@Getter
public class CustomSort {

    @NonNull
    private Sort sort;

    public CustomSort() {
        this(Sort.unsorted());
    }

    public Direction getDirection(@NonNull String property) {
        return Optional.of(sort)
                .map(v -> v.getOrderFor(property))
                .map(Order::getDirection)
                .orElse(null);
    }

    public void updateSort(@NonNull String property, Direction direction) {
        List<Order> orders = sort.stream()
                .filter(v -> !property.equals(v.getProperty()))
                .collect(Collectors.toList());
        if (Objects.nonNull(direction)) {
            orders.add(new Order(direction, property));
        }
        sort = Sort.by(orders);
    }

    public void copyFrom(@NonNull CustomSort sort) {
        this.sort = sort.getSort();
    }

    public CustomSort copy() {
        return new CustomSort(sort);
    }

    public boolean areSortsEquivalent(@NonNull CustomSort other) {
        List<Order> thisList = sort.stream().toList();
        List<Order> otherList = other.sort.stream().toList();

        if (thisList.size() != otherList.size()) {
            return false;
        }
        return IntStream.range(0, thisList.size())
                .allMatch(i -> ordersEqual(thisList.get(i), otherList.get(i)));
    }


    private boolean ordersEqual(Order o1, Order o2) {
        return Objects.equals(o1.getProperty(), o2.getProperty())
                && Objects.equals(o1.getDirection(), o2.getDirection())
                && o1.isIgnoreCase() == o2.isIgnoreCase()
                && o1.getNullHandling() == o2.getNullHandling();
    }
}
