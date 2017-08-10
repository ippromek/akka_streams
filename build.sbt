import sbt._
import sbt.Keys._


//
//
//lazy val allResolvers = Seq(
//  "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
//  "Akka Snapshot Repository" at "http://repo.akka.io/snapshots/"
// "JBoss" at "https://repository.jboss.org/"
//)

libraryDependencies ++= Seq(
 // "com.typesafe.akka" % "akka-actor_2.11" % "2.4.8",
  "com.typesafe.akka" % "akka-stream_2.11" % "2.5.0",
  "joda-time" % "joda-time" % "2.9.4")

lazy val AllLibraryDependencies =
  Seq(
    "com.typesafe.akka" % "akka-actor_2.11" % "2.5.0",
    "com.typesafe.akka" % "akka-http_2.11" % "3.0.0-RC1",
    "com.typesafe.akka" % "akka-http-core_2.11" % "3.0.0-RC1",
    "com.typesafe.akka" % "akka-http-spray-json_2.11" % "3.0.0-RC1"


  )
// https://mvnrepository.com/artifact/com.typesafe.akka/akka-http_2.11
//libraryDependencies += "com.typesafe.akka" % "akka-http_2.11" % "10.0.5"

// https://mvnrepository.com/artifact/com.typesafe.akka/akka-http-core_2.11
//libraryDependencies += "com.typesafe.akka" % "akka-http-core_2.11" % "10.0.5"

// https://mvnrepository.com/artifact/com.typesafe.akka/akka-http-spray-json_2.11
//libraryDependencies += "com.typesafe.akka" % "akka-http-spray-json_2.11" % "10.0.5"


// https://mvnrepository.com/artifact/com.typesafe.akka/akka-slf4j_2.11
libraryDependencies += "com.typesafe.akka" % "akka-slf4j_2.11" % "2.5.0"


// https://mvnrepository.com/artifact/org.slf4j/slf4j-api
libraryDependencies += "org.slf4j" % "slf4j-api" % "1.7.25"

// https://mvnrepository.com/artifact/com.typesafe.akka/akka-http-spray-json-experimental_2.11
//libraryDependencies += "com.typesafe.akka" % "akka-http-spray-json-experimental_2.11" % "2.4.11.1"

// https://mvnrepository.com/artifact/com.typesafe.akka/akka-remote_2.11
libraryDependencies += "com.typesafe.akka" % "akka-remote_2.11" % "2.5.0"

// https://mvnrepository.com/artifact/org.scalatest/scalatest_2.11
libraryDependencies += "org.scalatest" % "scalatest_2.11" % "2.2.6"




lazy val commonSettings = Seq(
  version := "1.0",
  scalaVersion := "2.11.8",
//  resolvers := allResolvers,
  libraryDependencies := AllLibraryDependencies
)


lazy val serverside =(project in file("serverside")).
  settings(commonSettings: _*).
  settings(
    name := "serverside"
  )
  .aggregate(common, clientside)
  .dependsOn(common, clientside)

lazy val common = (project in file("common")).
  settings(commonSettings: _*).
  settings(
    name := "common"
  )

lazy val clientside = (project in file("clientside")).
  settings(commonSettings: _*).
  settings(
    name := "clientside"
  )
  .aggregate(common)
  .dependsOn(common)
