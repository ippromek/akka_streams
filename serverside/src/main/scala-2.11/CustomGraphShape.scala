/**
  * Created by oleg.baydakov on 16/04/2017.
  * Akka documentation p.456 "Building reusable Graph components"
  */
// A shape represents the input and output ports of a reusable
// processing module

import java.io.BufferedReader

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.pattern.ask
import akka.stream._
import akka.stream.scaladsl.{Balance, Broadcast, Flow, GraphDSL, Keep, Merge, MergePreferred, RunnableGraph, Sink, Source, Tcp, Zip}
import akka.util.ByteString

import scala.collection.{immutable, mutable}
import scala.concurrent.duration._
import scala.util.{Failure, Success}
import FanInShape.{Init, Name}
import akka.event.Logging

import scala.concurrent.Future

trait AkkaStreamApp extends App {
  implicit val actorSystem = ActorSystem("Tutorial")
  implicit val materializer = ActorMaterializer()
  implicit val executor = actorSystem.dispatcher
}

object FromCloseableResource extends AkkaStreamApp {

  val in = this.getClass.getResourceAsStream("example_resource.txt")

  Source.unfoldResource[String, BufferedReader](
    () => scala.io.Source.fromInputStream(in).bufferedReader(),
    reader => Option(reader.readLine()),
    reader => reader.close())
    .runForeach(println)
}


// A shape represents the input and output ports of a reusable
// processing module
case class PriorityWorkerPoolShape[In, Out](
                                             jobsIn: Inlet[In],
                                             priorityJobsIn: Inlet[In],
                                             resultsOut: Outlet[Out]) extends Shape {

  // It is important to provide the list of all input and output
  // ports with a stable order. Duplicates are not allowed.
  override val inlets: immutable.Seq[Inlet[_]] =
  jobsIn :: priorityJobsIn :: Nil
  override val outlets: immutable.Seq[Outlet[_]] =
    resultsOut :: Nil

  // A Shape must be able to create a copy of itself. Basically
  // it means a new instance with copies of the ports
  override def deepCopy() = PriorityWorkerPoolShape(
    jobsIn.carbonCopy(),
    priorityJobsIn.carbonCopy(),
    resultsOut.carbonCopy())

  // A Shape must also be able to create itself from existing ports
  override def copyFromPorts(
                              inlets: immutable.Seq[Inlet[_]],
                              outlets: immutable.Seq[Outlet[_]]) = {
    assert(inlets.size == this.inlets.size)
    assert(outlets.size == this.outlets.size)
    // This is why order matters when overriding inlets and outlets
    //PriorityWorkerPoolShape(inlets(0), inlets(1), outlets(0))
    PriorityWorkerPoolShape(
      jobsIn.carbonCopy(),
      priorityJobsIn.carbonCopy(),
      resultsOut.carbonCopy())
  }
}

class PriorityWorkerPoolShape2[In, Out](_init: Init[Out] = Name("PriorityWorkerPool"))
  extends FanInShape[Out](_init) {
  protected override def construct(i: Init[Out]) = new PriorityWorkerPoolShape2(i)

  val jobsIn = newInlet[In]("jobsIn")
  val priorityJobsIn = newInlet[In]("priorityJobsIn")
  // Outlet[Out] with name "out" is automatically created
}


object PriorityWorkerPool {
  def apply[In, Out](
                      worker: Flow[In, Out, Any],
                      workerCount: Int): Graph[PriorityWorkerPoolShape[In, Out], NotUsed] = {
    GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val priorityMerge = b.add(MergePreferred[In](1))
      val balance = b.add(Balance[In](workerCount))
      val resultsMerge = b.add(Merge[Out](workerCount))



      // After merging priority and ordinary jobs, we feed them to the balancer
      priorityMerge.out ~> balance
// Wire up each of the outputs of the balancer to a worker flow
      // then merge them back
      for (i <- 0 until workerCount)
        balance.out(i) ~> worker ~> resultsMerge.in(i)
      // We now expose the input ports of the priorityMerge and the output
      // of the resultsMerge as our PriorityWorkerPool ports
      // -- all neatly wrapped in our domain specific Shape

      PriorityWorkerPoolShape(
        jobsIn = priorityMerge.in(0),
        priorityJobsIn = priorityMerge.preferred,
        resultsOut = resultsMerge.out)
    }
  }
}



object Demo_Custom extends App {

  implicit val system = ActorSystem("Sys")
  import system.dispatcher
  implicit val materializer = ActorMaterializer()


  val worker1 = Flow[String].map("step 1 " + _)
  val worker2 = Flow[String].map("step 2 " + _)



//  RunnableGraph.fromGraph(GraphDSL.create() { implicit b =>
    import GraphDSL.Implicits._


    // or provide custom

 //############################################################
//    val merge = b.add(Merge[Int](2))
//    val bcast = b.add(Broadcast[Int](2))
//    Source(1 to 100) ~> merge ~> Flow[Int].map { s => println(s); s } ~> bcast ~> Sink.ignore
//    merge <~ Flow[Int].buffer(10, OverflowStrategy.dropHead) <~ bcast

//###############################################################
 //   val priorityPool1 = b.add(PriorityWorkerPool(worker1, 4))
    //val priorityPool2 = b.add(PriorityWorkerPool(worker2, 2))

 //   Source(1 to 100).map("job: " + _) ~> priorityPool1.jobsIn
 //   Source(1 to 100).map("priority job: " + _) ~> priorityPool1.priorityJobsIn

    //priorityPool1.resultsOut ~> priorityPool2.jobsIn

    //Source(1 to 100).map("one-step, priority " + _) ~> priorityPool2.priorityJobsIn

    //priorityPool2.resultsOut ~> Sink.foreach(println)

   // priorityPool1.resultsOut ~> Sink.foreach(println)
 //   ClosedShape

//  }).run()

  //###########################################################################
// Source.single(0).log("before-map")
//    .withAttributes(Attributes.logLevels(onElement = Logging.WarningLevel))
//    .map(elem => println(elem)).runWith(Sink.ignore)

  //#############################################################################
  val naturalNumbers = Source(Stream.from(1))
  val in = Source.tick(1.second, 2.seconds, 1)

  val resultSink = Sink.foreach(println)

  //naturalNumbers.runWith(resultSink)

  val zipStream = Source.fromGraph(GraphDSL.create() { implicit builder =>
    val zip = builder.add(Zip[Int, Int])
    naturalNumbers ~> zip.in0
    in ~> zip.in1

    //zip.out ~> Sink.foreach(println)

   // ClosedShape
    SourceShape(zip.out)

  })

  val status:Future[Done]=zipStream.runWith(resultSink)

    status.onComplete {
    case Success(Done) =>
      println("Stream finished successfully.")
    case Failure(e) =>
      println(s"Stream failed with $e")
  }


  //system.shutdown()
}

