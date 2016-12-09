import sbt.Keys._
import sbt.Resolver

lazy val commonSettings = Seq(
  organization := "com.pagerduty",
  version := "0.1.0",
  scalaVersion := "2.11.8"
)

lazy val root = (project in file(".")).
  settings(commonSettings: _*).
  settings(
    name := "akka-cluster",
    resolvers += Resolver.jcenterRepo,
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-cluster" % "2.4.14",
      "com.typesafe.akka" %% "akka-cluster-tools" % "2.4.14",
      "com.typesafe.akka" %% "akka-persistence" % "2.4.14",
      "com.typesafe.akka" %% "akka-cluster-sharding" % "2.4.14",
      "com.hootsuite" %% "akka-persistence-redis" % "0.6.0"
    )
  )
