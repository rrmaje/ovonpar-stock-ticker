package actors

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

  case class EnterOrder(side: Byte, instrument: String, quantity: Double, price: Double, client: String)

  case class OrderResponse(orderId: String, side: Byte, instrument: String, quantity: Long, price: Long)

  case class OrdersRequest(client: String)

  case class OrdersResponse(orders: Vector[OrderResponse])

  case class CancelOrder(orderId: String)

  case class OrderEntered(orderId: String)

}

class OrderEntryApi(config: Config) extends Actor with akka.actor.ActorLogging {

  import OrderEntryApi._

  var orderIdGenerator = new OrderIDGenerator()

  var orderEntry: OrderEntry = null

  var instruments: Instruments = null

  var events: Events = null

  var orderTracker: OrderTracker = null

  val message: POE.EnterOrder = new POE.EnterOrder()

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

    log.info("\nOrder ID\n----------------\n{}\n\n", orderId)

    sender() ! OrderEntered(orderId)

  }

  def orders(client: String) {

    var Orders = new Orders(client)

    val ordersResult = Orders.collect(events, orderTracker)

    val ordersResponse = ordersResult.asScala.map(x => OrderResponse(x.getOrderId, x.getSide, ASCII.unpackLong(x.getInstrument).trim(), x.getQuantity, x.getPrice)).toVector

    sender() ! OrdersResponse(ordersResponse);

  }

  def receive = {
    case ConnectToOrderEntry =>
      open(config)
    case EnterOrder(side, instrumentCode, quantity, price, client) =>
      enterOrder(side, quantity, instrumentCode, price, client)
    case OrdersRequest(client) =>
      orders(client)
    case _ => Unit
  }

  override def preStart {

  }

  override def postStop {
    this.orderEntry.close();
  }

}

