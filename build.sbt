organization := "de.jnmeyr"
name := "ambiio"
version := "0.1"
scalaVersion := "2.13.1"

libraryDependencies += "org.apache.commons" % "commons-math3" % "3.6.1"
libraryDependencies += "org.apache.commons" % "commons-collections4" % "4.4"
libraryDependencies += "org.openmuc" % "jrxtx" % "1.0.1"
libraryDependencies += "org.scala-lang.modules" %% "scala-parser-combinators" % "1.1.2"
libraryDependencies += "org.typelevel" %% "cats-effect" % "2.1.2"
libraryDependencies += "com.github.scopt" %% "scopt" % "4.0.0-RC2"

lazy val root = (project in file("ambiio"))
  .enablePlugins(AssemblyPlugin)
  .settings(
    organization := "de.jnmeyr",
    name := "ambiio",
    version := "0.1",
    scalaVersion := "2.13.1"
  ).settings(
    mainClass in assembly := Some("de.jnmeyr.ambiio.Ambiio")
  )
