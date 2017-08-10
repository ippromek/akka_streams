/**
  * Created by oleg.baydakov on 17/04/2017.
  * Mastering Akka "Handling streaming I/O
  */

import akka.actor.ActorSystem
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Tcp.{IncomingConnection, ServerBinding}
import akka.stream.scaladsl.{Flow, Framing, Sink, Source, Tcp}
import akka.util.ByteString

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object TcpServerCalculator extends App{
  implicit val system = ActorSystem("ClientAndServer")
  import system.dispatcher
  implicit val materializer = ActorMaterializer()


  val Calculation = """(\d+)(?:\s*([-+*\/])\s*((?:\s[- +])?\d+)\s*)+$""".r
  val calcFlow = Flow[String].
    map{
      case Calculation(a, "+", b) => a.toInt + b.toInt
      case Calculation(a, "-", b) => a.toInt - b.toInt
      case Calculation(a, "*", b) => a.toInt * b.toInt
      case Calculation(a, "/", b) => a.toInt / b.toInt
      case other => 0
    }

  val connections:
    Source[IncomingConnection, Future[ServerBinding]] =
    Tcp().bind("localhost", 8888)

  connections runForeach { connection =>
    println(s"New connection from: ${connection.remoteAddress}")
    val calc = Flow[ByteString].
      via(Framing.delimiter(
        ByteString("\n"),
        maximumFrameLength = 256,
        allowTruncation = true)).
      map(_.utf8String).
      via(calcFlow).
      map(i => ByteString(s"$i\n"))
    connection.handleWith(calc)
  }

  //system.shutdown()

}
