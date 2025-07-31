package org.ost.advertisement.dto.sort;

import static java.util.Optional.ofNullable;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.Getter;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.domain.Sort.Order;

public class CustomSort {

	@Getter
	private Sort sort;

	public CustomSort() {
		this(Sort.unsorted());
	}

	public CustomSort(Sort sort) {
		this.sort = sort;
	}

	public Direction getDirection(String property) {
		return ofNullable(sort)
			.map(v -> v.getOrderFor(property))
			.map(Order::getDirection)
			.orElse(null);
	}

	public void updateSort(String property, Direction direction) {
		List<Order> orders = sort.stream().filter(v -> !property.equals(v.getProperty()))
			.collect(Collectors.toList());
		if (Objects.nonNull(direction)) {
			orders.add(new Order(direction, property));
		}
		sort = Sort.by(orders);
	}

	public void copyFrom(CustomSort sort) {
		this.sort = sort.getSort();
	}

	public CustomSort copy() {
		return new CustomSort(sort);
	}

	public void clear() {
		this.sort = Sort.unsorted();
	}

	public boolean areSortsEquivalent(CustomSort newSort) {
		List<Order> list1 = this.getSort().stream().toList();
		List<Sort.Order> list2 = newSort.getSort().stream().toList();

		if (list1.size() != list2.size()) {
			return false;
		}

		for (int i = 0; i < list1.size(); i++) {
			Sort.Order o1 = list1.get(i);
			Sort.Order o2 = list2.get(i);
			if (!Objects.equals(o1.getProperty(), o2.getProperty())) {
				return false;
			}
			if (!Objects.equals(o1.getDirection(), o2.getDirection())) {
				return false;
			}
		}

		return true;
	}
}
