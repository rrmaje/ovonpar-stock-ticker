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
import actors.OrderEntryApi
import com.paritytrading.parity.net.poe.POE
import actors.OrderEntryApi.ConnectToOrderEntry
import actors.OrderEntryApi.EnterOrder
import akka.actor.ActorRef
import scala.concurrent.ExecutionContextExecutor
import actors.OrderEntryApi.OrderEntered
import play.api.libs.json.JsError
import play.api.libs.json.Json
import scala.concurrent.Future
import actors.OrderEntryApi.OrdersRequest

@Singleton
class Application @Inject() (cc: ControllerComponents, configuration: play.api.Configuration)(implicit system: ActorSystem, mat: Materializer) extends AbstractController(cc) with OrdersApi {

  implicit def executionContext: ExecutionContextExecutor = system.dispatcher

  val logger: Logger = Logger("application")

  val publisher = system.actorOf(Props[MarketDataPublisher], "market-data-publisher")

  new Thread(new MarketDataReceiver(configuration.underlying, publisher)).start

  def createOrderEntryApi(): ActorRef = {
    val orderEntry = system.actorOf(OrderEntryApi.props(configuration.underlying), "order-entry")
    orderEntry ! ConnectToOrderEntry
    orderEntry
  }

  def newOrder = Action.async(parse.json) { request =>
    val newOrderJson = request.body.validate[EnterOrder]
    newOrderJson.fold(
      errors => {
        Future.successful(BadRequest(Json.obj("status" -> "Error", "message" -> JsError.toJson(errors))))
      },
      newOrder => {
        enterOrder(newOrder).map { orderEntered =>
          Ok(Json.toJson(orderEntered))
        }

      })
  }
  
  def orders = Action.async(parse.json) { request =>
    val ordersJson = request.body.validate[OrdersRequest]
    ordersJson.fold(
      errors => {
        Future.successful(BadRequest(Json.obj("status" -> "Error", "message" -> JsError.toJson(errors))))
      },
      ordersRequest => {
        getOrders(ordersRequest).map { ordersResponse =>
          Ok(Json.toJson(ordersResponse))
        }

      })
  }

  def data = WebSocket.accept[JsValue, JsValue] { request =>
    ActorFlow.actorRef { out =>
      Props(new MarketDataRelay(publisher, out))
    }
  }
}
