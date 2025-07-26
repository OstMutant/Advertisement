package org.ost.advertisement.ui.views.sort;

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
	private Sort sort = Sort.unsorted();

	public CustomSort() {

	}

	public Direction getDirection(String property) {
		return ofNullable(sort)
			.map(v -> v.getOrderFor(property))
			.map(Order::getDirection)
			.orElse(null);
	}

	public void updateSort(String property, Order order) {
		List<Order> orders = sort.stream().filter(v -> !property.equals(v.getProperty()))
			.collect(Collectors.toList());
		if (Objects.nonNull(order)) {
			orders.add(order);
		}
		sort = Sort.by(orders);
	}
}
