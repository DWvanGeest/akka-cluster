package com.pagerduty.sample.akkacluster

import akka.actor.ActorSystem

trait ClusterMember {
  def run: ActorSystem
  def shutdown: Unit = {}
}
