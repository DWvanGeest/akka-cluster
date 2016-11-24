package com.pagerduty.sample.akkacluster

import akka.actor.{Actor, ActorLogging}

object Settings {
  val ActorName = "settings"

  sealed trait Mode
  case object Upcase extends Mode
  case object Stub extends Mode

  case object FetchMode
  case class ModeFetched(mode: Mode)
}


class Settings extends Actor with ActorLogging {
  import Settings._

  def receive = {
    case FetchMode =>
      log.info("Settings returning ModeFetched")
      sender() ! ModeFetched(Upcase)
  }

}
