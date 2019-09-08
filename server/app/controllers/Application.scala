package controllers

import javax.inject.Inject
import akka.actor.ActorSystem
import javax.inject._
import play.api._
import play.api.mvc._
import akka.actor.Props
import play.api.libs.json.JsValue
import play.api.libs.streams.ActorFlow
import akka.stream.Materializer
import actors.oe.OrderEntryApi
import com.paritytrading.parity.net.poe.POE
import actors.oe.OrderEntryApi.ConnectToOrderEntry
import actors.oe.OrderEntryApi.EnterOrder
import actors.oe.OrderEntryApi.ClientOrder
import akka.actor.ActorRef
import scala.concurrent.ExecutionContextExecutor
import actors.oe.OrderEntryApi.OrderEntered
import play.api.libs.json.JsError
import play.api.libs.json.Json
import scala.concurrent.Future
import actors.oe.OrderEntryApi.OrdersRequest
import actors.reporter.MarketEventsReceiver
import actors.reporter.MarketEventsPublisher
import actors.oe.OrdersApi
import actors.reporter.MarketDataPublisher
import actors.reporter.MarketDataReceiver
import actors.reporter.MarketDataRelay
import actors.reporter.MarketEventsRelay
import akka.stream.OverflowStrategy
import actors.reporter.MarketReporter.TradesRequest
import com.paritytrading.parity.util.{ Instrument, Instruments }
import scala.collection.JavaConverters._
import actors.oe.OrderEntryApi.CancelOrder
import actors.reporter.MarketEventsMailer
import actors.reporter.MarketReporter.PMRInstrumentsRequest
import play.api.libs.ws._
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class Application @Inject() (ws: WSClient, cc: AppControllerComponents, configuration: play.api.Configuration, clientAction: ClientAction)(implicit system: ActorSystem, mat: Materializer) extends AppBaseController(cc) with OrdersApi {

  val logger: Logger = Logger("application")

  val publisher = system.actorOf(Props[MarketDataPublisher], "market-data-publisher")

  new Thread(new MarketDataReceiver(configuration.underlying, publisher)).start

  val marketEventsPublisher = system.actorOf(Props[MarketEventsPublisher], "market-events-publisher")
  
  system.actorOf(Props(new MarketEventsMailer(ws, configuration,  marketEventsPublisher,cc.userRepo)), "market-events-mailer")

  new Thread(new MarketEventsReceiver(configuration.underlying, marketEventsPublisher)).start
  
 
  def createOrderEntryApi(): ActorRef = {
    val orderEntry = system.actorOf(OrderEntryApi.props(configuration.underlying), "order-entry")
    orderEntry
  }

  def newOrder = Action.async(parse.json) { request =>
    val newOrderJson = request.body.validate[ClientOrder]
    newOrderJson.fold(
      errors => {
        Future.successful(BadRequest(Json.obj("status" -> 400, "message" -> JsError.toJson(errors))))
      },
      newOrder => {
        enterOrder(newOrder).map { orderEntered =>
          Ok(Json.toJson(orderEntered))
        }

      })
  }

  def securedOrder = clientAction.async(parse.json) { implicit request =>
    val newOrderJson = request.body.validate[EnterOrder]
    newOrderJson.fold(
      errors => {
        Future.successful(BadRequest(Json.obj("status" -> 400, "message" -> JsError.toJson(errors))))
      },
      newOrder => {
        val clientOrder = ClientOrder(newOrder.side, newOrder.instrument, newOrder.quantity, newOrder.price, request.username)
        enterOrder(clientOrder).map { orderEntered =>
          Ok(Json.obj("status" -> 200, "message" -> Json.toJson(orderEntered)))
        }

      })
  }

  def cancel = clientAction.async(parse.json) { implicit request =>
    (request.body \ "orderId").asOpt[String].map { orderId =>
      cancelOrder(new CancelOrder(request.username, orderId))
      Future.successful(Ok(Json.obj("status" -> 200, "message" -> "Instruction sent")))
    }.getOrElse {
      Future.successful(BadRequest(Json.obj("status" -> 400, "message" -> "Incorrect params")))
    }
  }

  def orders = clientAction.async { request =>
    {
      getOrders(OrdersRequest(request.username)).map { ordersResponse =>
        var instruments = Instruments.fromConfig(configuration.underlying, "instruments")
        var instrumentsJson = for (instrument <- instruments.asScala) yield {
          Json.obj(
            "instrument" -> instrument.asString(),
            "priceFactor" -> instrument.getPriceFactor(),
            "sizeFactor" -> instrument.getSizeFactor(),
            "priceFractionDigits" -> instrument.getPriceFractionDigits(),
            "sizeFractionDigits" -> instrument.getSizeFractionDigits())

        }
        Ok(Json.obj("status" -> 200, "message" -> Json.obj("instruments" -> instrumentsJson, "ordersResponse" -> Json.toJson(ordersResponse))))
      }
    }
  }

  def data = WebSocket.accept[JsValue, JsValue] { request =>
    ActorFlow.actorRef { out =>
      Props(new MarketDataRelay(publisher, out))
    }
  }
  /* oveflowstrategy is dropNew - if buffer size is exceeded, elements will be dropped and not sent to client socket
   * see WebSockets may drop outgoing messages #6246
   */
  def trades = WebSocket.acceptOrResult[JsValue, JsValue] { request =>
    val userKey = request.getQueryString(ClientAction.OST_KEY).getOrElse(None)
    val rNo = new scala.util.Random().nextInt(1000000)
    logger.debug(s"[$rNo] Connection to Market Reporting with user key:$userKey")
    Future.successful(
      userKey match {
        case None => Left(Forbidden)
        case u: String => {
          val usr = new String(cc.ostKey.key.open(u.asInstanceOf[String].getBytes))
          logger.debug(s"[$rNo] Client request: ${usr}")
          Right(ActorFlow.actorRef({ out =>
            Props(new MarketEventsRelay(marketEventsPublisher, out, usr))
          }, 100))
        }
      })
  }

}
