organization := "de.jnmeyr"
name := "ambiio"
version := "0.2"
scalaVersion := "2.13.1"

libraryDependencies += "com.github.scopt" %% "scopt" % "4.0.0-RC2"
libraryDependencies += "org.apache.commons" % "commons-math3" % "3.6.1"
libraryDependencies += "org.apache.commons" % "commons-collections4" % "4.4"
libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-dsl" % "0.21.6",
  "org.http4s" %% "http4s-blaze-server" % "0.21.6",
  "org.http4s" %% "http4s-blaze-client" % "0.21.6"
)
libraryDependencies += "org.openmuc" % "jrxtx" % "1.0.1"
libraryDependencies += "org.scala-lang.modules" %% "scala-parser-combinators" % "1.1.2"
libraryDependencies ++= Seq(
  "org.slf4j" % "slf4j-api" % "1.7.5",
  "org.slf4j" % "slf4j-simple" % "1.7.5"
)
libraryDependencies += "org.typelevel" %% "cats-effect" % "2.1.2"

lazy val root = (project in file("."))
  .enablePlugins(AssemblyPlugin)
  .settings(
    organization := "de.jnmeyr",
    name := "ambiio",
    version := "0.2",
    scalaVersion := "2.13.1"
  ).settings(
    mainClass in assembly := Some("de.jnmeyr.ambiio.Ambiio")
  )

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-language:higherKinds",
  "-language:postfixOps",
  "-language:implicitConversions",
  "-feature",
  //"-Xfatal-warnings",
)
