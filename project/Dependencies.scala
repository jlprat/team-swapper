import sbt._

object Dependencies {
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.2.0"
  
  lazy val akkaVersion = "2.6.6"
  lazy val akkaHttpVersion = "10.1.12"
  lazy val actorTyped = "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion
  lazy val actorTestkitTyped = "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion
  lazy val akkaStream = "com.typesafe.akka" %% "akka-stream" % akkaVersion

  lazy val akkaHttp = "com.typesafe.akka" %% "akka-http" % akkaHttpVersion
  lazy val akkaHttpTestkit = "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion
  
  lazy val sprayJson = "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion
}
