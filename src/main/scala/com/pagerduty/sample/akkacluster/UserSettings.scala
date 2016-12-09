package com.pagerduty.sample.akkacluster

import akka.actor.{Actor, ActorLogging, Props}
import akka.cluster.sharding.ShardRegion
import akka.persistence.{PersistentActor, SnapshotOffer}

object UserSettings {
  val ActorName = "settings"

  sealed trait Mode
  case object Upcase extends Mode
  case object Stub extends Mode

  case class FetchMode(userId: Int)
  case class ModeFetched(userId: Int, mode: Mode)

  case class SetMode(userId: Int, newMode: Mode)

  case class SettingsState(mode: Mode = Upcase, modeChangesNum: Int = 0)

  val idExtractor: ShardRegion.ExtractEntityId = {
    case fm: FetchMode => (fm.userId.toString, fm)
    case sm: SetMode    => (sm.userId.toString, sm)
  }

  val numberOfShards = 10

  val shardResolver: ShardRegion.ExtractShardId = msg => msg match {
    case fm: FetchMode   => (math.abs(fm.userId.hashCode) % numberOfShards).toString
    case sm: SetMode => (math.abs(sm.userId.hashCode) % numberOfShards).toString
  }
}


class UserSettings extends PersistentActor with ActorLogging {
  val userId = self.path.name
  log.info(s"UserSettings starting up for id $userId")

  override def persistenceId = s"user-settings-$userId"

  import UserSettings._

  var state = SettingsState()

  val receiveCommand: Receive = {
    case FetchMode(uId) =>
      log.info(s"Settings for user $userId returning mode: ${state.mode} num: ${state.modeChangesNum}")
      sender() ! ModeFetched(uId, state.mode)
    case msg @ SetMode(uId, _) =>
      persist(msg) { msg =>
        updateMode(msg)
        log.info(s"Mode set to ${msg.newMode} for user $userId")

        if ((state.modeChangesNum % 3) == 0) {
          log.info("Saving snapshot!")
          saveSnapshot(state)
        }
      }
  }

  val receiveRecover: Receive = {
    case msg: SetMode =>
      log.info(s"Recovering state with message $msg")
      updateMode(msg)
    case SnapshotOffer(_, snapshot: SettingsState) =>
      log.info(s"Recovering state from snapshot $snapshot")
      state = snapshot
  }

  private def updateMode(msg: SetMode): Unit = {
    state = SettingsState(msg.newMode, state.modeChangesNum + 1)
  }

}
