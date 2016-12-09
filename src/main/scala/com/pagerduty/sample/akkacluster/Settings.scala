package com.pagerduty.sample.akkacluster

import akka.actor.{Actor, ActorLogging}
import akka.persistence.PersistentActor

object Settings {
  val ActorName = "settings"

  sealed trait Mode
  case object Upcase extends Mode
  case object Stub extends Mode

  case object FetchMode
  case class ModeFetched(mode: Mode)

  case class SetMode(newMode: Mode)

  case class SettingsState(mode: Mode = Upcase, modeChangesNum: Int = 0)
}


class Settings extends PersistentActor with ActorLogging {
  override def persistenceId = "settings-actor"

  import Settings._

  var state = SettingsState()

  val receiveCommand: Receive = {
    case FetchMode =>
      log.info(s"Settings returning mode: ${state.mode} num: ${state.modeChangesNum}")
      sender() ! ModeFetched(state.mode)
    case msg: SetMode =>
      persist(msg) { msg =>
        updateMode(msg)
        log.info(s"Mode set to $msg.newMode")
      }
  }

  val receiveRecover: Receive = {
    case msg: SetMode =>
      log.info(s"Recovering state with message $msg")
      updateMode(msg)
  }

  private def updateMode(msg: SetMode): Unit = {
    state = SettingsState(msg.newMode, state.modeChangesNum + 1)
  }

}
