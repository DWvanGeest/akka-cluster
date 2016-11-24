package com.pagerduty.sample.akkacluster

import akka.pattern.gracefulStop
import akka.actor._
import akka.cluster.ClusterEvent.{CurrentClusterState, MemberUp}
import com.typesafe.config.ConfigFactory
import akka.cluster.{Cluster, Member, MemberStatus}
import com.pagerduty.sample.akkacluster.TransformerMaster.RegisterSlave

import scala.concurrent.duration.Duration
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object TransformerSlaveMember {
  val Role = "TransformerSlave"

  def apply(port: Int): TransformerSlaveMember = new TransformerSlaveMember(port)
}

class TransformerSlaveMember(port: Int) extends ClusterMember {
  var slaveRef: ActorRef = _
  var system: ActorSystem = _

  def run(): ActorSystem = {
    val config = ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$port").
      withFallback(ConfigFactory.parseString(s"akka.cluster.roles = [${TransformerSlaveMember.Role}]")).
      withFallback(ConfigFactory.load())

    system = ActorSystem(SeedMember.ClusterName, config)
    println(s"Started ${TransformerSlaveMember.Role} cluster!")

    slaveRef = system.actorOf(Props[TransformerSlave], name = TransformerSlave.ActorName)
    system
  }

  override def shutdown(): Unit = {
    println("********** Shutting down slave...")
    val stopped = gracefulStop(slaveRef, 5 seconds)
    Await.result(stopped, Duration.Inf)
    println("**************** Slave shutdown")
  }
}

object TransformerSlave {
  val ActorName = "transformer-slave"

  case class TransformText(textId: String, text: String)
  case class TextTransformed(textId: String, text: String)
}

class TransformerSlave extends Actor with ActorLogging {
  import TransformerSlave._

  val cluster = Cluster(context.system)

  // subscribe to cluster changes, MemberUp
  // re-subscribe when restart
  override def preStart(): Unit = {
    cluster.subscribe(self, classOf[MemberUp])
  }
  override def postStop(): Unit = cluster.unsubscribe(self)

  def receive = {
    case TransformText(id, text) =>
      log.info(s"Transforming text with ID $id")
      sender() ! TextTransformed(id, text.toUpperCase())
    case state: CurrentClusterState =>
      state.members.filter(_.status == MemberStatus.Up) foreach register
    case MemberUp(m) =>
      register(m)
  }

  private def register(member: Member): Unit = {
    sendToMasters(member, RegisterSlave)
  }

  private def sendToMasters(member: Member, message: Any): Unit = {
    if (member.hasRole(TransformerMasterMember.Role)) {
      val masters = context.actorSelection(RootActorPath(member.address) / "user" / TransformerMaster.ActorName)
      masters ! message
    }
  }
}

