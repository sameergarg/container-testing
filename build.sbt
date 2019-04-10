name := "container-testing"

version := "0.1"

scalaVersion := "2.12.8"

val slickLibs = Seq(
  "com.typesafe.slick" %% "slick" % "3.3.0",
  "org.slf4j" % "slf4j-nop" % "1.6.4",
  "com.typesafe.slick" %% "slick-hikaricp" % "3.3.0",
)

val dockerTestkitLibs = Seq(
  "com.whisk" %% "docker-testkit-scalatest" % "0.9.8" % "test",
  "com.whisk" %% "docker-testkit-impl-spotify" % "0.9.8" % "test",
  "com.whisk" %% "docker-testkit-config" % "0.9.8" % "test"
)

libraryDependencies ++= Seq(
  "org.flywaydb" % "flyway-core" % "5.2.4",
  "org.postgresql" % "postgresql" % "42.2.5",
  "io.monix" %% "monix" % "2.3.3",
  "org.scalatest" %% "scalatest" % "3.0.5" % "test",
) ++ 
  slickLibs ++
  dockerTestkitLibs