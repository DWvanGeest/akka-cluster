package com.pagerduty.sample.akkacluster

import akka.cluster.Cluster

object AkkaCluster extends App {
  val mode = args(0)
  val port = args(1).toInt

  println(s"Akka Cluster starting up with mode $mode on port $port!")

  val member = mode match {
    case "slave" => TransformerSlaveMember(port)
    case "master" => TransformerMasterMember(port)
    case _ => SeedMember(port)
  }

  val system = member.run

  sys.addShutdownHook {
    member.shutdown
    println("Leaving cluster...")
    val cluster = Cluster(system)
    cluster.leave(cluster.selfAddress)
    println("Left cluster.")
    Thread.sleep(5000)
  }
}
