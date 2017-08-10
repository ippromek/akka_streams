/**
  * Created by oleg.baydakov on 11/03/2017.
  */

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl._
import akka.http.scaladsl.model.ws._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import akka.stream.scaladsl._
//import common.{Item, JsonSupport}
import scala.io.StdIn
import scala.concurrent.Future
import akka.http.scaladsl.model.ws._
import akka.stream._
import akka.stream.scaladsl._
import akka.http.scaladsl.marshallers.sprayjson._
import spray.json._

import org.scalatest.F

class HelloTests extends FunSuite {
  test("displaySalutation returns 'Hello World'") {
    assert(Hello.displaySalutation == "Hello World")
  }
}



final case class Item(name: String, id: Long)

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val itemFormat = jsonFormat2(Item)
}

object Demo extends App with Directives with JsonSupport {

  implicit val system = ActorSystem("my-system")
  implicit val materializer = ActorMaterializer()

  // needed for the future flatMap/onComplete in the end
  implicit val executionContext = system.dispatcher


  val (websocketSink, websocketSource) =
    MergeHub.source[String].toMat(BroadcastHub.sink[String])(Keep.both).run()

  val websocketFlow: Flow[Message, Message, NotUsed] =
    Flow[Message].mapAsync(1) {
      // transform websocket message to domain message (string)
      case TextMessage.Strict(text) =>       Future.successful(text)
      case streamed: TextMessage.Streamed => streamed.textStream.runFold("")(_ ++ _)
    }.via(Flow.fromSinkAndSource(websocketSink, websocketSource))
      .map[Message](string => TextMessage(string))


  val route =
    path("hello") {
      get {
        complete(HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          "<h1>Say hello to akka-http</h1>"))
      }
    } ~
      path("randomitem") {
        get {
          // will marshal Item to JSON
          complete(Item("thing", 42))
        }
      } ~
      path("saveitem") {
        post {
          // will unmarshal JSON to Item
          entity(as[Item]) { item =>
            println(s"Server saw Item : $item")
            complete(item)
          }
        }
      } ~
      path("websocket") {
        get {
          handleWebSocketMessages(websocketFlow)
        }
      } ~
      path("sendmessagetowebsocket" / IntNumber) { msgCount =>
        post {
          for(i <- 0 until msgCount)
          {
            Source.single(s"sendmessagetowebsocket $i").runWith(websocketSink)
          }
          complete("done")
        }
      }



  val (host, port) = ("localhost", 8088)
  val bindingFuture = Http().bindAndHandle(route, host, port)

  bindingFuture.onFailure {
    case ex: Exception =>
      println(s"$ex Failed to bind to $host:$port!")
  }

  println(s"Server online at http://localhost:8088/\nPress RETURN to stop...")
  StdIn.readLine() // let it run until user presses return
  bindingFuture
    .flatMap(_.unbind()) // trigger unbinding from the port
    .onComplete(_ => system.terminate()) // and shutdown when done



}
