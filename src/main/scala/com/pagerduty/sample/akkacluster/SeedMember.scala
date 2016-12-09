package com.pagerduty.sample.akkacluster

import akka.actor._
import akka.cluster.sharding.ClusterSharding
import com.typesafe.config.ConfigFactory

object SeedMember {
  val ClusterName = "TransformerCluster"
  val Role = "seed"

  def apply(port: Int): SeedMember = new SeedMember(port)
}

class SeedMember(port: Int) extends ClusterMember {
  import SeedMember._

  def init: ActorSystem = {
    val config = ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$port").
      withFallback(ConfigFactory.parseString(s"akka.cluster.roles = [$Role]")).
      withFallback(ConfigFactory.load())

    val system = ActorSystem(ClusterName, config)

    println("Started seed cluster!")
    system
  }
}
