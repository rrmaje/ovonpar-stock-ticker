package actors.oe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class OrderTracker {

	private Map<String, List<String>> orders;

	OrderTracker() {
		orders = new HashMap<String, List<String>>();
	}

	synchronized void add(String username, String orderId) {
		List<String> o = orders.computeIfAbsent(username, key -> new ArrayList<String>());

		o.add(orderId);

	}
	
	synchronized boolean remove(String username, String orderId) {
		List<String> o = orders.get(username);
		if (o == null) {
			return false;
		} else {
			return o.remove(orderId);
		}
	}

	synchronized boolean contains(String username, String orderId) {
		List<String> o = orders.get(username);
		if (o == null) {
			return false;
		} else {
			return o.contains(orderId);
		}
	}
}
