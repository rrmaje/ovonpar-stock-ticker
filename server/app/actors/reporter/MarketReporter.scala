package actors.reporter

import akka.actor.{ Actor, ActorRef }
import com.paritytrading.parity.net.pmr.{ PMR, PMRParser }
import com.paritytrading.parity.util.{ Instrument, Instruments }
import com.paritytrading.nassau.util.SoupBinTCP;
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
import scala.concurrent.ExecutionContext
import akka.util.Timeout
import scala.concurrent.duration._
import akka.pattern.ask
import controllers.EmailByParityUser
import scala.concurrent.ExecutionContext.Implicits.global

import play.api.libs.ws._
import play.api.libs.json._
import scala.concurrent.Future
import play.api.db.Database
import model.UserRepository
import javax.inject.Inject
import akka.pattern.AskTimeoutException

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
    
    val address = Configs.getInetAddress(config, "trade-report.address");
    val port = Configs.getPort(config, "trade-report.port");
    val username = config.getString("trade-report.username");
    val password = config.getString("trade-report.password");

    var instruments = Instruments.fromConfig(config, "instruments")

    publisher ! instruments

    SoupBinTCP.receive(new InetSocketAddress(address, port), username, password,
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

class MarketEventsPublisher extends Actor with ActorLogging {

  import MarketReporter._

  var instruments: Instruments = null
  var trades = Vector[TradeEvent]()

  def receive = {
    case trade: TradeEvent =>
      trades = trades :+ trade
      context.system.eventStream.publish(trade)
    case instruments: Instruments =>
      this.instruments = instruments
      log.debug("Received instruments")
      context.system.eventStream.publish(instruments)
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

class MarketEventsRelay(publisher: ActorRef, out: ActorRef, client: String) extends Actor with ActorLogging {

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

class MarketEventsMailer(ws: WSClient, config: play.api.Configuration, publisher: ActorRef, userService: UserRepository) extends Actor with ActorLogging {

  import MarketReporter._

  implicit val timeout = Timeout(5 seconds)

  var instruments: Instruments = null

  var fmt: MailFormat = null

  val mailerUrl = config.get[String]("mailer.url")

  override def preStart {
    context.system.eventStream.subscribe(self, classOf[Instruments])
  }

  override def postStop {
    context.system.eventStream.unsubscribe(self, classOf[MarketEvent])
    context.system.eventStream.unsubscribe(self, classOf[Instruments])
  }

  def receive = {
    case trade: TradeEvent =>
      val contacts = for {
        b <- emailFromParityUsr(trade.buyer)
        s <- emailFromParityUsr(trade.seller)
      } yield (b, s)
      contacts.map(f => f match {
        case (Some(b: String), Some(s: String)) => {
          val body = String.format(fmt.format(trade), b, s)
          log.debug("Formatted mail event:{}", body)
          sendMail("%d-%d".format(trade.matchNumber, trade.buyOrderNumber), b, body)
          sendMail("%d-%d".format(trade.matchNumber, trade.sellOrderNumber), s, body)
        }
        case _ => log.error(s"Failed to get parity user for trade number: ${trade.matchNumber}")
      })

    case instruments: Instruments =>
      this.instruments = instruments
      fmt = new MailFormat(instruments)
      context.system.eventStream.subscribe(self, classOf[MarketEvent])
    case _ =>
      Unit
  }

  private def emailFromParityUsr(usr: String) = {
    userService.findByParityUser(usr).map { res =>
      val userTuple = res.getOrElse(None)
      userTuple match {
        case u: model.User => Some(u.username)
        case _: AskTimeoutException => {
          log.error(s"Timeout getting user with parity id: ${usr}")
        }
        case None =>
      }
    }
  }

  private def sendMail(id: String, receipient: String, body: String) {

    val request: WSRequest = ws.url(mailerUrl)
    val data = Json.obj(
      "username" -> receipient,
      "html" -> body)

    val mailerRequest: WSRequest =
      request
        .addHttpHeaders("Accept" -> "application/json")
        .addHttpHeaders("X-Request-Id" -> id)
        .withRequestTimeout(10000.millis)

    val futureResult: Future[Int] = mailerRequest.post(data).map { response =>
      (response.json \ "status").as[Int]
    }

    futureResult.map(f => {
      f match {
        case 200 => log.debug(s"Sent match notification with X-Request-Id: $id")
        case _   => log.error(s"Failed to send match notification with X-Request-Id: $id")
      }
    })
  }

}
