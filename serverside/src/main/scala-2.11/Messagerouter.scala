/**
  * Created by oleg.baydakov on 21/04/2017.
  * Reactive Messaging Patterns with the Actor Model
  * https://github.com/VaughnVernon/ReactiveMessagingPatterns_ActorModel/blob/master/src/co/vaughnvernon/reactiveenterprise/messagerouter/MessageRouter.scala
  */
import akka.actor._

class CompletableApp(val steps:Int) extends App {
  val canComplete = new java.util.concurrent.CountDownLatch(1);
  val canStart = new java.util.concurrent.CountDownLatch(1);
  val completion = new java.util.concurrent.CountDownLatch(steps);

  val system_new = ActorSystem("eaipatterns")

  def awaitCanCompleteNow = canComplete.await

  def awaitCanStartNow = canStart.await

  def awaitCompletion = {
    completion.await
    system_new.terminate()
  }

  def canCompleteNow() = canComplete.countDown()

  def canStartNow() = canStart.countDown()

  def completeAll() = {
    while (completion.getCount > 0) {
      completion.countDown()
    }
  }

  def completedStep() = completion.countDown()
}

object MessageRouterDriver extends CompletableApp(20) {
  val processor1 = system_new.actorOf(Props[Processor], "processor1")
  val processor2 = system_new.actorOf(Props[Processor], "processor2")

  val alternatingRouter = system_new.actorOf(Props(classOf[AlternatingRouter], processor1, processor2), "alternatingRouter")

  for (count <- 1 to 10) {
    alternatingRouter ! "Message #" + count
  }

  awaitCompletion

  println("MessageRouter: is completed.")
}

class AlternatingRouter(processor1: ActorRef, processor2: ActorRef) extends Actor {
  var alternate = 1;

  def alternateProcessor() = {
    if (alternate == 1) {
      alternate = 2
      processor1
    } else {
      alternate = 1
      processor2
    }
  }

  def receive = {
    case message: Any =>
      val processor = alternateProcessor
      println(s"AlternatingRouter: routing $message to ${processor.path.name}")
      processor ! message
      MessageRouterDriver.completedStep()
  }
}

class Processor extends Actor {
  def receive = {
    case message: Any =>
      println(s"Processor: ${self.path.name} received $message")
      MessageRouterDriver.completedStep()
  }
}
