package com.pagerduty.sample.akkacluster

import akka.actor.ActorSystem

trait ClusterMember {
  def run: ActorSystem = {
    val system = init()

    system
  }

  def init(): ActorSystem
  def shutdown(): Unit = {}
}
