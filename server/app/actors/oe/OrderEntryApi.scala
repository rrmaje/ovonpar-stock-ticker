package actors.oe

import java.net.InetSocketAddress
import akka.actor.Actor
import com.paritytrading.parity.util.Instruments
import com.paritytrading.parity.util.OrderIDGenerator
import org.jvirtanen.config.Configs
import com.typesafe.config.Config
import com.paritytrading.nassau.soupbintcp.SoupBinTCP
import com.paritytrading.foundation.ASCII
import com.paritytrading.parity.util.Instrument
import com.paritytrading.parity.net.poe.POE
import akka.actor.Props
import scala.collection.JavaConverters._

object OrderEntryApi {
  def props(config: Config): Props = Props(new OrderEntryApi(config))

  case object ConnectToOrderEntry

  case class EnterOrder(side: Byte, instrument: String, quantity: Double, price: Double)

  case class ClientOrder(side: Byte, instrument: String, quantity: Double, price: Double, client: String)

  case class OrderResponse(orderId: String, side: Byte, instrument: String, quantity: Long, price: Long)

  case class OrdersRequest(client: String)

  case class OrdersResponse(orders: Vector[OrderResponse])

  case class CancelOrder(client: String, orderId: String)

  case class OrderEntered(orderId: String)

}

class OrderEntryApi(config: Config) extends Actor with akka.actor.ActorLogging {

  import OrderEntryApi._
  import actors.oe.Events;
  import actors.oe.OrderEntry;
  import actors.oe.OrderTracker;
  import actors.oe.Orders;

  var orderIdGenerator = new OrderIDGenerator()

  var orderEntry: OrderEntry = null

  var instruments: Instruments = null

  var events: Events = null

  var orderTracker: OrderTracker = null

  val message: POE.EnterOrder = new POE.EnterOrder()

  val cancelMessage: POE.CancelOrder = new POE.CancelOrder()

  private def open(config: Config) {
    val orderEntryAddress = Configs.getInetAddress(config, "order-entry.address")
    val orderEntryPort = Configs.getPort(config, "order-entry.port")
    val orderEntryUsername = config.getString("order-entry.username")
    val orderEntryPassword = config.getString("order-entry.password")

    val address = new InetSocketAddress(orderEntryAddress, orderEntryPort)

    this.instruments = Instruments.fromConfig(config, "instruments")

    this.events = new Events()

    this.orderTracker = new OrderTracker()

    this.orderEntry = OrderEntry.open(address, events);

    var loginRequest = new SoupBinTCP.LoginRequest();

    ASCII.putLeft(loginRequest.username, orderEntryUsername)
    ASCII.putLeft(loginRequest.password, orderEntryPassword)
    ASCII.putRight(loginRequest.requestedSession, "")
    ASCII.putLongRight(loginRequest.requestedSequenceNumber, 0)

    orderEntry.getTransport().login(loginRequest)

    log.info("Order Entry opened at " + orderEntry.getTransport.getChannel.getRemoteAddress())

  }

  def enterOrder(side: Byte, quantity: Double, instrumentCode: String, price: Double, client: String) {

    message.side = side

    val orderId = this.orderIdGenerator.next()
    val instrument = ASCII.packLong(instrumentCode);
    val config = instruments.get(instrument)

    ASCII.putLeft(message.orderId, orderId)
    message.quantity = Math.round(quantity * config.getSizeFactor())
    message.instrument = instrument
    message.price = Math.round(price * config.getPriceFactor())
    message.client = ASCII.packLong(client)

    this.orderEntry.send(message)

    this.orderTracker.add(client, orderId)

    log.info("Order ID:{}, Client:{}", orderId, client)

    sender() ! OrderEntered(orderId)

  }

  def orders(client: String) {

    var Orders = new Orders(client)

    log.debug(s"Fetching Orders - Client: ${client}")

    val ordersResult = Orders.collect(events, orderTracker)

    val ordersResponse = ordersResult.asScala.map(x => OrderResponse(x.getOrderId, x.getSide, ASCII.unpackLong(x.getInstrument).trim(), x.getQuantity, x.getPrice)).toVector

    sender() ! OrdersResponse(ordersResponse);

  }

  def cancelOrder(client: String, orderId: String) {

    ASCII.putLeft(cancelMessage.orderId, orderId)
    cancelMessage.quantity = 0

    this.orderEntry.send(cancelMessage)
    
    this.orderTracker.remove(client, orderId)

    log.info("Cancel Order instruction sent, Order ID:{}, Client:{}", orderId, client)

  }

  def receive = {
    case ConnectToOrderEntry =>
      open(config)
    case ClientOrder(side, instrumentCode, quantity, price, client) =>
      enterOrder(side, quantity, instrumentCode, price, client)
    case OrdersRequest(client) =>
      orders(client)
    case CancelOrder(client, orderId) =>
      cancelOrder(client, orderId)
    case _ => Unit
  }

  override def preStart {

  }

  override def postStop {
    this.orderEntry.close();
  }

}

