name := "track-processor"
 
version := "1.0"
 
scalaVersion := "2.10.3"
 
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.6",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.6" % "test",
  "com.typesafe" % "config" % "1.2.1",
  "com.github.scala-incubator.io" %% "scala-io-file" % "0.4.2",
  "com.google.apis" % "google-api-services-storage" % "v1-rev26-1.19.1",
  "com.rabbitmq" % "amqp-client" % "latest.integration",
  "io.spray" %%  "spray-json" % "1.3.1"
  )