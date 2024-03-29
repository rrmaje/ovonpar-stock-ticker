package actors.reporter;


import com.paritytrading.foundation.ASCII;
import com.paritytrading.parity.net.pmr.PMR;
import com.paritytrading.parity.net.pmr.PMRListener;
import com.paritytrading.parity.util.Timestamps;
import java.util.HashMap;
import java.util.Map;

public class TradeProcessor implements PMRListener {

    private final Map<Long, Order> orders;

    private final PMRTrade trade;

    private final TradeListener listener;

    public TradeProcessor(TradeListener listener) {
        this.orders = new HashMap<>();

        this.trade = new PMRTrade();

        this.listener = listener;
    }

    @Override
    public void version(PMR.Version message) {
        if (message.version != PMR.VERSION)
        	System.err.println("error: " + "Unsupported protocol version");
    }

    @Override
    public void orderEntered(PMR.OrderEntered message) {
        orders.put(message.orderNumber, new Order(message));
        listener.orderEntered(message);
    }

    @Override
    public void orderAdded(PMR.OrderAdded message) {
    }

    @Override
    public void orderCanceled(PMR.OrderCanceled message) {
        Order order = orders.get(message.orderNumber);

        order.remainingQuantity -= message.canceledQuantity;

        if (order.remainingQuantity == 0)
            orders.remove(message.orderNumber);
        
        listener.orderCanceled(message);
    }

    @Override
    public void trade(PMR.Trade message) {
        Order resting  = orders.get(message.restingOrderNumber);
        Order incoming = orders.get(message.incomingOrderNumber);

        Order buy  = resting.side == PMR.BUY  ? resting : incoming;
        Order sell = resting.side == PMR.SELL ? resting : incoming;

        long buyOrderNumber = resting.side == PMR.BUY ?
                message.restingOrderNumber : message.incomingOrderNumber;

        long sellOrderNumber = resting.side == PMR.SELL ?
                message.restingOrderNumber : message.incomingOrderNumber;

        trade.timestamp       = Timestamps.format(message.timestamp / 1_000_000);
        trade.matchNumber     = message.matchNumber;
        trade.instrument      = ASCII.unpackLong(resting.instrument).trim();
        trade.quantity        = message.quantity;
        trade.price           = resting.price;
        trade.buyer           = ASCII.unpackLong(buy.client).trim();
        trade.buyOrderNumber  = buyOrderNumber;
        trade.seller          = ASCII.unpackLong(sell.client).trim();
        trade.sellOrderNumber = sellOrderNumber;

        listener.trade(trade);

        resting.remainingQuantity  -= message.quantity;
        incoming.remainingQuantity -= message.quantity;

        if (resting.remainingQuantity == 0)
            orders.remove(message.restingOrderNumber);

        if (incoming.remainingQuantity == 0)
            orders.remove(message.incomingOrderNumber);
    }

    private static class Order {
        long client;
        byte side;
        long instrument;
        long price;
        long remainingQuantity;

        Order(PMR.OrderEntered message) {
            this.client          = message.client;
            this.side              = message.side;
            this.instrument        = message.instrument;
            this.price             = message.price;
            this.remainingQuantity = message.quantity;
        }
    }

}
