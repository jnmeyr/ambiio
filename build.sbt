organization := "de.jnmeyr"
name := "ambiio"
version := "0.6"
scalaVersion := "2.13.3"

libraryDependencies += "com.github.scopt" %% "scopt" % "4.0.0-RC2"
libraryDependencies += "dev.zio" %% "zio" % "1.0.1"
libraryDependencies += "dev.zio" %% "zio-interop-cats" % "2.1.4.0"
libraryDependencies ++= Seq(
  "io.circe" %% "circe-generic" % "0.13.0",
  "io.circe" %% "circe-literal" % "0.13.0"
)
libraryDependencies += "org.apache.commons" % "commons-math3" % "3.6.1"
libraryDependencies += "org.apache.commons" % "commons-collections4" % "4.4"
libraryDependencies += "org.eclipse.paho" % "org.eclipse.paho.client.mqttv3" % "1.2.5"
libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-blaze-server" % "0.21.6",
  "org.http4s" %% "http4s-blaze-client" % "0.21.6",
  "org.http4s" %% "http4s-dsl" % "0.21.6",
  "org.http4s" %% "http4s-circe" % "0.21.6"
)
libraryDependencies += "org.openmuc" % "jrxtx" % "1.0.1"
libraryDependencies += "org.scala-lang.modules" %% "scala-parser-combinators" % "1.1.2"
libraryDependencies ++= Seq(
  "org.slf4j" % "slf4j-api" % "1.7.5",
  "org.slf4j" % "slf4j-simple" % "1.7.5"
)
libraryDependencies += "org.typelevel" %% "cats-effect" % "2.1.4"

libraryDependencies += "org.scalatest" %% "scalatest-wordspec" % "3.2.0" % "test"

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-feature",
  "-language:higherKinds",
  "-language:postfixOps",
  "-language:implicitConversions",
  "-unchecked",
  "-Xfatal-warnings",
)

lazy val root = (project in file("."))
  .enablePlugins(AssemblyPlugin)
  .settings(
    mainClass in assembly := Some("de.jnmeyr.ambiio.EffectAmbiio")
  )
