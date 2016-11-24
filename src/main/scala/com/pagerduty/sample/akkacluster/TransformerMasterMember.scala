package com.pagerduty.sample.akkacluster

import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

import akka.pattern.AskableActorRef
import akka.actor.{Actor, ActorRef, ActorSystem, LoggingFSM, Props, Terminated}
import akka.util.Timeout
import com.pagerduty.sample.akkacluster.TransformerMaster.TransformWork
import com.pagerduty.sample.akkacluster.TransformerSlave.{TextTransformed, TransformText}
import com.typesafe.config.ConfigFactory

import scala.util.Random
import scala.concurrent.duration._

/**
  * Created by dvangeest on 10/11/16.
  */
object TransformerMasterMember {
  val Role = "TransformerMaster"

  def apply(port: Int): TransformerMasterMember = new TransformerMasterMember(port)
}

class TransformerMasterMember(port: Int) extends ClusterMember {
  def init(): ActorSystem = {
    val config = ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$port").
      withFallback(ConfigFactory.parseString(s"akka.cluster.roles = [${TransformerMasterMember.Role}]")).
      withFallback(ConfigFactory.load())

    val system = ActorSystem(SeedMember.ClusterName, config)
    println(s"Started ${TransformerMasterMember.Role} member!")

    val master = new AskableActorRef(system.actorOf(Props[TransformerMaster], name = TransformerMaster.ActorName))

    val counter = new AtomicInteger
    import system.dispatcher
    system.scheduler.schedule(1.seconds, 5.seconds) {
      implicit val timeout = Timeout(5 seconds)
      (master ? TransformWork("transformation job " + counter.incrementAndGet())) onSuccess {
        case result => println(s"Received transformation result $result")
      }
    }

    system
  }
}

object TransformerMaster {
  val ActorName = "transformer-master"

  case class TransformWork(work: String)
  case class WorkTransformed(work: String)
  case class WorkNotTransformed(error: String)

  case object RegisterSlave

  sealed trait State
  case object Executing extends State

  case class Data(slaves: Set[ActorRef], jobs: Map[String, ActorRef])
}

class TransformerMaster extends Actor with LoggingFSM[TransformerMaster.State, TransformerMaster.Data] {
  import TransformerMaster._

  startWith(Executing, Data(Set.empty, Map.empty))

  when(Executing) {
    case Event(TransformWork(_), Data(slaves, _)) if slaves.isEmpty =>
      log.info("No slaves registered, returning error")
      sender() ! WorkNotTransformed("Service unavailable!")
      stay()

    case Event(TransformWork(work), Data(slaves, jobs)) =>
      val textId = UUID.randomUUID().toString
      log.info(s"Received work for transformation, assigning ID $textId")

      Random.shuffle(slaves).head ! TransformText(textId, work)

      log.info(s"Work with id $textId sent to worker...")

      goto(Executing) using Data(slaves, jobs + (textId -> sender()))

    case Event(TextTransformed(id, transformedText), Data(slaves, jobs)) =>
      log.info(s"Work with id $id transformed! Returning to requestor...")
      jobs(id) ! WorkTransformed(transformedText)

      goto(Executing) using Data(slaves, jobs - id)

    case Event(RegisterSlave, Data(slaves, jobs)) =>
      if (!slaves.contains(sender())) {
        log.info(s"Registering ${sender()} as new slave")
        context.watch(sender())
        goto(Executing) using Data(slaves + sender, jobs)
      } else {
        log.info(s"Already knew about ${sender()}, no need to register")
        stay()
      }

    case Event(Terminated(slave), Data(slaves, jobs)) =>
      log.info(s"Deregistering slave ${slave}")
      goto(Executing) using Data(slaves - slave, jobs)
  }
}
