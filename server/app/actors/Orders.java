package actors;

import static java.util.Comparator.*;
import static java.util.stream.Collectors.*;


import java.util.HashMap;
import java.util.List;
import java.util.Map;

class Orders extends DefaultEventVisitor {

	private final Map<String, Order> orders;
	
	private final String username;

	public Orders(String username) {
		orders = new HashMap<>();
		this.username = username;
	}

	List<Order> collect(Events events, OrderTracker orderTracker) {

		events.accept(this);

		return getEvents(orderTracker);
	}

	private List<Order> getEvents(OrderTracker orderTracker) {
		return orders.values().stream().filter(c -> orderTracker.contains(username, c.getOrderId()))
				.sorted(comparing(Order::getTimestamp)).collect(toList());
	}

	@Override
	public void visit(Event.OrderAccepted event) {
		orders.put(event.orderId, new Order(event));
	}

	@Override
	public void visit(Event.OrderExecuted event) {
		Order order = orders.get(event.orderId);
		if (order == null)
			return;

		order.apply(event);

		if (order.getQuantity() <= 0)
			orders.remove(event.orderId);
	}

	@Override
	public void visit(Event.OrderCanceled event) {
		Order order = orders.get(event.orderId);
		if (order == null)
			return;

		order.apply(event);

		if (order.getQuantity() <= 0)
			orders.remove(event.orderId);
	}
	

}
