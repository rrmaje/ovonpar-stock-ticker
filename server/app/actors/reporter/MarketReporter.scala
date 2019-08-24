package actors.reporter

import akka.actor.{ Actor, ActorRef }
import com.paritytrading.parity.net.pmr.{ PMR, PMRParser }
import com.paritytrading.parity.util.{ Instrument, Instruments }
import com.paritytrading.nassau.util.MoldUDP64
import com.typesafe.config.Config
import java.net.InetSocketAddress
import org.jvirtanen.config.Configs
import scala.collection.JavaConverters._
import akka.actor.actorRef2Scala
import play.api.libs.json.Json
import akka.actor.ActorLogging
import controllers.SystemKey
import play.api.libs.json.JsValue
import com.paritytrading.foundation.ASCII

object MarketReporter {

  sealed trait MarketEvent

  case class TradeEvent(
    instrument:      String,
    price:           Long,
    timestamp:       String,
    matchNumber:     Long,
    quantity:        Long,
    buyer:           String,
    buyOrderNumber:  Long,
    seller:          String,
    sellOrderNumber: Long) extends MarketEvent

  case class TradesRequest(client: String)

  case object MarketEventsRequest

  case object PMRInstrumentsRequest

}

class MarketEventsReceiver(config: Config, publisher: ActorRef) extends Runnable {

  import MarketReporter._

  override def run {
    val multicastInterface = Configs.getNetworkInterface(config, "market-report.multicast-interface")
    val multicastGroup = Configs.getInetAddress(config, "market-report.multicast-group")
    val multicastPort = Configs.getPort(config, "market-report.multicast-port")
    val requestAddress = Configs.getInetAddress(config, "market-report.request-address")
    val requestPort = Configs.getPort(config, "market-report.request-port")

    var instruments = Instruments.fromConfig(config, "instruments")

    publisher ! instruments

    MoldUDP64.receive(
      multicastInterface,
      new InetSocketAddress(multicastGroup, multicastPort),
      new InetSocketAddress(requestAddress, requestPort),
      new PMRParser(new TradeProcessor(new TradeListener {

        override def version(message: PMR.Version) {

        }

        override def orderEntered(message: PMR.OrderEntered) {

        }

        override def orderCanceled(message: PMR.OrderCanceled) {

        }
        override def trade(message: PMRTrade) {
          publisher ! TradeEvent(
            message.instrument,
            message.price,
            message.timestamp,
            message.matchNumber,
            message.quantity,
            message.buyer,
            message.buyOrderNumber,
            message.seller,
            message.sellOrderNumber)
        }

      })))
  }
}

class MarketEventsPublisher extends Actor {

  import MarketReporter._

  var instruments: Instruments = null
  var trades = Vector[TradeEvent]()

  def receive = {
    case trade: TradeEvent =>
      trades = trades :+ trade
      context.system.eventStream.publish(trade)
    case instruments: Instruments =>
      this.instruments = instruments
    case PMRInstrumentsRequest =>
      sender ! instruments
    case MarketEventsRequest =>
      trades.foreach(sender ! _)
    case TradesRequest(client) => {
      trades.filter(p =>
        p.buyer.equals(client) || p.seller.equals(client)).foreach(sender ! _)
    }
  }

}

class MarketEventsRelay(publisher: ActorRef, out: ActorRef, client:String) extends Actor with ActorLogging {

  import MarketReporter._

  override def preStart {
    publisher ! PMRInstrumentsRequest
  }

  override def postStop {
    context.system.eventStream.unsubscribe(self, classOf[MarketEvent])
  }

  def receive = {
    case trade: TradeEvent if trade.buyer.equals(client) || trade.seller.equals(client) =>
      out ! Json.obj(
        "type" -> "Trade",
        "timestamp" -> trade.timestamp,
        "instrument" -> trade.instrument,
        "price" -> trade.price,
        "size" -> trade.quantity,
        if (trade.buyer.equals(client)) "buyOrderNumber" -> trade.buyOrderNumber else "sellOrderNumber" -> trade.sellOrderNumber,
        "matchNumber" -> trade.matchNumber)
    case instruments: Instruments =>
      instruments.asScala.foreach { instrument =>
        out ! Json.obj(
          "type" -> "Instrument",
          "instrument" -> instrument.asString(),
          "priceFactor" -> instrument.getPriceFactor(),
          "sizeFactor" -> instrument.getSizeFactor(),
          "priceFractionDigits" -> instrument.getPriceFractionDigits(),
          "sizeFractionDigits" -> instrument.getSizeFractionDigits())
      }
      
      context.system.eventStream.subscribe(self, classOf[MarketEvent])
      
      publisher ! TradesRequest(client)

    case _ =>
      Unit
  }

}
