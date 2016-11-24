lazy val commonSettings = Seq(
  organization := "com.pagerduty",
  version := "0.1.0",
  scalaVersion := "2.12.0"
)

lazy val root = (project in file(".")).
  settings(commonSettings: _*).
  settings(
    name := "akka-cluster",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-cluster" % "2.4.12"
    )
  )
