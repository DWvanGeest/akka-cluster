package com.pagerduty.sample.akkacluster

import akka.pattern.{AskableActorRef, gracefulStop}
import akka.actor._
import akka.cluster.ClusterEvent.{CurrentClusterState, MemberUp}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings, ClusterSingletonProxy, ClusterSingletonProxySettings}
import com.typesafe.config.ConfigFactory
import akka.cluster.{Cluster, Member, MemberStatus}
import akka.util.Timeout
import com.pagerduty.sample.akkacluster.UserSettings._
import com.pagerduty.sample.akkacluster.TransformerMaster.{RegisterSlave, TransformWork}

import scala.concurrent.duration.Duration
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Random

object TransformerSlaveMember {
  val Role = "TransformerSlave"

  def apply(port: Int): TransformerSlaveMember = new TransformerSlaveMember(port)
}

class TransformerSlaveMember(port: Int) extends ClusterMember {
  var slaveRef: ActorRef = _
  var system: ActorSystem = _

  def init(): ActorSystem = {
    val config = ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$port").
      withFallback(ConfigFactory.parseString(s"akka.cluster.roles = [${TransformerSlaveMember.Role}]")).
      withFallback(ConfigFactory.load())

    system = ActorSystem(SeedMember.ClusterName, config)

//    system.actorOf(
//      ClusterSingletonManager.props(
//        singletonProps = Props(classOf[Settings]),
//        terminationMessage = PoisonPill,
//        settings = ClusterSingletonManagerSettings(system).withRole(TransformerSlaveMember.Role)),
//      name = Settings.ActorName)


    val userSettingsRegion: ActorRef = ClusterSharding(system).start(
      typeName = "UserSettings",
      entityProps = Props[UserSettings],
      settings = ClusterShardingSettings(system),
      extractEntityId = UserSettings.idExtractor,
      extractShardId = UserSettings.shardResolver)

    if (port == 3001) {
      val usersToStub = Random.shuffle(1 to 100).drop(50)

      println(s"********************* Stubbed users $usersToStub")

      usersToStub foreach { uId => userSettingsRegion ! SetMode(uId, Stub) }
    }

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

  case class TransformText(uId: Int, textId: String, text: String)
  case class TextTransformed(uId: Int, textId: String, text: String)
}

class TransformerSlave extends Actor with ActorLogging {
  import TransformerSlave._

  val cluster = Cluster(context.system)

  val settingsRef = new AskableActorRef(userSettingRegion())

  // subscribe to cluster changes, MemberUp
  // re-subscribe when restart
  override def preStart(): Unit = {
    cluster.subscribe(self, classOf[MemberUp])
  }
  override def postStop(): Unit = cluster.unsubscribe(self)

  def receive = {
    case TransformText(uId, id, text) =>
      log.info(s"Transforming text with ID $id")
      implicit val timeout = Timeout(20 seconds)
      val fMode = settingsRef ? UserSettings.FetchMode(uId) // quick and dirty hack

      val master = sender() // don't close over mutable state...

      import context.dispatcher
      fMode foreach { mode =>
        val transformed = mode match {
          case ModeFetched(_, Upcase) => text.toUpperCase()
          case ModeFetched(_, Stub) => s"SOME STUB TEXT FOR ID $id"
        }

        master ! TextTransformed(uId, id, transformed)
      }
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

  private def userSettingRegion(): ActorRef = {
    ClusterSharding(context.system).shardRegion("UserSettings")
  }
}

