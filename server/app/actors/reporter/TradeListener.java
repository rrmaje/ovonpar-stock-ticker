package actors.reporter;

import com.paritytrading.parity.net.pmr.PMR;

public interface TradeListener {

	public void trade(PMRTrade event);

	public void version(PMR.Version message);

	public void orderEntered(PMR.OrderEntered message);

	public void orderCanceled(PMR.OrderCanceled message);

}
