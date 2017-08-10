/**
  * Created by oleg.baydakov on 15/04/2017.
  */


import java.io.File

import akka.stream._
import akka.stream.scaladsl._
import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.util.ByteString

import scala.concurrent._
import scala.concurrent.duration._
import java.nio.file.Paths

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import com.typesafe.config.ConfigFactory


/**
  * Created by oleg.baydakov on 15/04/2017.
  */
sealed trait Transaction
case class Deposit(acct:String, amt:Int) extends Transaction
case class Withdraw(acct:String, amt:Int) extends Transaction


object AkkaStremaTest extends App {

  // val confPath = getClass.getResource("/application.conf")
  // val config = ConfigFactory.parseFile(new File(confPath.getPath)).resolve()
  //val value = config.getString("akka.logging-filter")
  //println(s"My secret value is $value")


  implicit val as = ActorSystem("QuickStart")
  implicit val ec = as.dispatcher
//  val settings = ActorMaterializerSettings(as).withDebugLogging(true).withFuzzing(true)
  implicit val mat = ActorMaterializer()

// exanmple #1
/* val done= Source.repeat("qwe")
    .zip(Source.fromIterator(()=>Iterator.from(0)))
    .take(7).mapConcat { case (s: String, n: Int) =>
    val i = " " * n
    f"$i$s%n"
  }.toMat(Sink.foreach(print))(Keep.right)
  .run()

  done.onComplete(_ => as.terminate()) */

  // EXAMPLE #2

  val bigCsv1=Source.combine(
    Source.single(("Type","Name","Amount")),
    Source.repeat("Deposit,Jane Dane, 1").take(5)
  )(Concat(_))

  val bigCsv=Source.repeat("Deposit,Jane Dane,1").take(100)

  val rows=Flow[String].map(line => line.split(",").toList)

  val parse = Flow[List[String]].collect{
    case List("Deposit",acct,amt) => Deposit(acct, amt.toInt)
    case List("Withdraw",acct,amt) => Withdraw(acct, amt.toInt)
  }

  val applyTxn= Sink.fold(Map.empty[String, Int]){
    (state: Map[String,Int], t:Transaction) =>
      {
        t match {
          case Deposit(acct,amt) => state.updated(acct, state.getOrElse(acct,0)+amt)
          case Withdraw(acct,amt) => state.updated(acct, state.getOrElse(acct,0)-amt)
        }

      }
   }

  bigCsv
      .map(line => line.split(",").toList)
      .collect{
      case List("Deposit",acct,amt) => Deposit(acct, amt.toInt)
      case List("Withdraw",acct,amt) => Withdraw(acct, amt.toInt)
              }
      .toMat(applyTxn)(Keep.right).run



  val parseAndApply = rows.via(parse).toMat(applyTxn)(Keep.right)

  val testPipeLine = bigCsv.toMat(parseAndApply)(Keep.right)

  testPipeLine.run.foreach(println)

 // Await.result(testPipeLine.run.foreach(println),1 second)


  //bigCsv.runForeach(println)

  //bigCsv.via(rows).runForeach(println)




  //Await.ready(system.whenTerminated,Duration.Inf)


}