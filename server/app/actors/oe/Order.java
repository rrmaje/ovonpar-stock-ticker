package actors.oe;

public class Order {

    private final long   timestamp;
    private final String orderId;
    private final byte   side;
    private final long   instrument;
    private       long   quantity;
    private final long   price;
    public final long   client;

    long getPrice() {
		return price;
	}

	Order(Event.OrderAccepted event) {
        this.timestamp   = event.timestamp;
        this.orderId     = event.orderId;
        this.side        = event.side;
        this.instrument  = event.instrument;
        this.quantity    = event.quantity;
        this.price       = event.price;
        this.client       = event.client;
    }

    void apply(Event.OrderExecuted event) {
        quantity -= event.quantity;
    }

    void apply(Event.OrderCanceled event) {
        quantity -= event.canceledQuantity;
    }

    long getTimestamp() {
        return timestamp;
    }

    byte getSide() {
        return side;
    }

    long getInstrument() {
        return instrument;
    }

    String getOrderId() {
        return orderId;
    }

    long getQuantity() {
        return quantity;
    }
    
    long getClient() {
        return client;
    }

}
