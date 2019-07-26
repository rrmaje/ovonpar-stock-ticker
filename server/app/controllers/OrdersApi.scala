package controllers

import actors.OrderEntryApi.OrderEntered
import play.api.libs.json.Json
import actors.OrderEntryApi.EnterOrder
import akka.actor.ActorRef
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import akka.util.Timeout
import akka.pattern.ask
import actors.OrderEntryApi.OrdersRequest
import actors.OrderEntryApi.OrdersResponse
import actors.OrderEntryApi.OrderResponse


trait OrdersApi {

  implicit val enterOrderFormat = Json.format[EnterOrder]
  implicit val orderEnteredFormat = Json.format[OrderEntered]
  implicit val ordersRequestFormat = Json.format[OrdersRequest]
  implicit val orderResponseFormat = Json.format[OrderResponse]
  implicit val ordersResponseFormat = Json.format[OrdersResponse]

  def createOrderEntryApi(): ActorRef

  implicit def executionContext: ExecutionContext
  implicit val timeout = Timeout(5 seconds)

  lazy val orderEntryApi: ActorRef = createOrderEntryApi()

  def enterOrder(order: EnterOrder): Future[OrderEntered] = {
    (orderEntryApi ? order).mapTo[OrderEntered]
  }

  def getOrders(ordersRequest: OrdersRequest): Future[OrdersResponse] = {
    (orderEntryApi ? ordersRequest).mapTo[OrdersResponse]
  }

}