/**
  * Created by oleg.baydakov on 11/03/2017.
  * https://github.com/sachabarber/SachaBarber.AkkaExamples/blob/master/Streams/src/main/scala-2.11/com/sas/basicflows/BasicFlowDemo.scala
  */
import java.io.File

import akka.actor.Status.{Failure, Success}
import akka.{Done, NotUsed}
import akka.actor._

import akka.util.{ByteString, Timeout}

import scala.concurrent.duration._
import scala.concurrent.forkjoin.ThreadLocalRandom
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import akka.stream._
import akka.stream.scaladsl._
import scala.util.{Success => ScalaSuccess}
import scala.util.{Failure => ScalaFailure}



case object DoneMessage

class HelloActor extends Actor {
  def receive = {
    case "hello" => {
      println("hello message seen by HelloActor")
    }
    case DoneMessage => {
      println(s"DoneMessage seen by HelloActor")
    }
  }
}



class BasicFlowDemo()  {

  implicit val system = ActorSystem("StreamsSystem")
  implicit val materializer = ActorMaterializer()



  def end() : Unit = {
    system.terminate()
  }

  def simpleFlow() : Unit = {
    val source = Source(1 to 10)
    val sink = Sink.fold[Int, Int](0)(_ + _)
    // connect the Source to the Sink, obtaining a RunnableGraph
    val runnable: RunnableGraph[Future[Int]] = source.toMat(sink)(Keep.right)
    // materialize the flow and get the value of the FoldSink
    implicit val timeout = Timeout(5 seconds)
    val sumFuture: Future[Int] = runnable.run()
    val sum = Await.result(sumFuture, timeout.duration)
    println(s"source.toMat(sink)(Keep.right) Sum = $sum")

    // Use the shorthand source.runWith(sink)
    val sumFuture2: Future[Int] = source.runWith(sink)
    val sum2 = Await.result(sumFuture2, timeout.duration)
    println(s"source.runWith(sink) Sum = $sum")
  }


  def differentSourcesAndSinks() : Unit = {
    //various sources
    Source(List(1, 2, 3)).runWith(Sink.foreach(println))
    Source.single("only one element").runWith(Sink.foreach(println))
    //actor sink
    val helloActor = system.actorOf(Props[HelloActor], name = "helloactor")
    Source(List("hello", "hello"))
      .runWith(Sink.actorRef(helloActor,DoneMessage))
    //future source
    val futureString = Source.fromFuture(Future.successful("Hello Streams!"))
      .toMat(Sink.head)(Keep.right).run()
    implicit val timeout = Timeout(5 seconds)
    val theString = Await.result(futureString, timeout.duration)
    println(s"theString = $theString")
  }



  def mapFlow() : Unit = {
    val source = Source(11 to 16)
    val doublerSource = source.map(x => x * 2)
    val sink = Sink.foreach(println)
    implicit val timeout = Timeout(5 seconds)

    // Use the shorthand source.runWith(sink)
    val printSinkFuture: Future[Done] = doublerSource.runWith(sink)
    Await.result(printSinkFuture, timeout.duration)
  }
}

class WritePrimesDemo {

  def run(): Unit = {
    implicit val system = ActorSystem("Sys")
    import system.dispatcher
    implicit val materializer = ActorMaterializer()

    // generate random numbers
    val maxRandomNumberSize = 1000000
    val primeSource: Source[Int, NotUsed] =
      Source.fromIterator(() => Iterator.continually(ThreadLocalRandom.current().nextInt(maxRandomNumberSize))).
        // filter prime numbers
        filter(rnd => isPrime(rnd)).
        // and neighbor +2 is also prime
        filter(prime => isPrime(prime + 2))

    // write to file sink
    val fileSink = FileIO.toPath(new File("target/primes.txt").toPath)
    val slowSink = Flow[Int]
      // act as if processing is really slow
      .map(i => { Thread.sleep(1000); ByteString(i.toString) })
      .toMat(fileSink)((_, bytesWritten) => bytesWritten)

    // console output sink
    val consoleSink = Sink.foreach[Int](println)

    // send primes to both slow file sink and console sink using graph API
    val graph = GraphDSL.create(slowSink, consoleSink)((slow, _) => slow) { implicit builder =>
      (slow, console) =>
        import GraphDSL.Implicits._
        val broadcast = builder.add(Broadcast[Int](2)) // the splitter - like a Unix tee
        primeSource ~> broadcast ~> slow // connect primes to splitter, and one side to file
        broadcast ~> console // connect other side of splitter to console
        ClosedShape
    }
    val materialized = RunnableGraph.fromGraph(graph).run()


    // ensure the output file is closed and the system shutdown upon completion
    materialized.onComplete {
      case ScalaSuccess(_) =>
        system.terminate()
      case ScalaFailure(e) =>
        println(s"Failure: ${e.getMessage}")
        system.terminate()
    }





  }

  def isPrime(n: Int): Boolean = {
    if (n <= 1) false
    else if (n == 2) true
    else !(2 to (n - 1)).exists(x => n % x == 0)
  }
}

object Demo1 extends App {

  //  BASIC FLOWS
 // val basicFlowDemo = new BasicFlowDemo()
  //basicFlowDemo.simpleFlow()
  //  basicFlowDemo.differentSourcesAndSinks()
  //  basicFlowDemo.mapFlow()
  //  basicFlowDemo.end()

  val writePrimesDemo = new WritePrimesDemo()
      writePrimesDemo.run()
}
