package actors.oe

import play.api.libs.json.Json
import akka.actor.ActorRef
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import akka.util.Timeout
import akka.pattern.ask
import actors.oe.OrderEntryApi.EnterOrder
import actors.oe.OrderEntryApi.ClientOrder
import actors.oe.OrderEntryApi.OrderEntered
import actors.oe.OrderEntryApi.OrdersRequest
import actors.oe.OrderEntryApi.OrderResponse
import actors.oe.OrderEntryApi.OrdersResponse
import actors.oe.OrderEntryApi.CancelOrder

trait OrdersApi {

  implicit val enterOrderFormat = Json.format[EnterOrder]
  implicit val clientOrderFormat = Json.format[ClientOrder]
  implicit val orderEnteredFormat = Json.format[OrderEntered]
  implicit val ordersRequestFormat = Json.format[OrdersRequest]
  implicit val orderResponseFormat = Json.format[OrderResponse]
  implicit val ordersResponseFormat = Json.format[OrdersResponse]
  implicit val cancelOrderFormat = Json.format[CancelOrder]

  def createOrderEntryApi(): ActorRef

  implicit def executionContext: ExecutionContext
  implicit val timeout = Timeout(5 seconds)

  lazy val orderEntryApi: ActorRef = createOrderEntryApi()

  def enterOrder(order: ClientOrder): Future[OrderEntered] = {
    (orderEntryApi ? order).mapTo[OrderEntered]
  }

  def getOrders(ordersRequest: OrdersRequest): Future[OrdersResponse] = {
    (orderEntryApi ? ordersRequest).mapTo[OrdersResponse]
  }

  def cancelOrder(cancelOrder: CancelOrder) = {
    orderEntryApi ! cancelOrder
  }

}