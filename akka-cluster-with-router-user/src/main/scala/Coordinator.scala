import akka.actor.{PoisonPill, Actor, Props}
import akka.routing.FromConfig

import scala.concurrent.duration._
import scala.collection.mutable

object Coordinator {
  case object Overview
  case object ProcessMessage
  case object TerminateSystem
  def props = Props[Coordinator]
}

class Coordinator extends Actor {
  import Coordinator._
  import context.dispatcher

  val router = context.actorOf(EmptyStringActor.props.withRouter(FromConfig), "router")

  val messageScheduler = context.system.scheduler.schedule(5.seconds, 1.milliseconds, self, ProcessMessage)
  val overviewScheduler = context.system.scheduler.schedule(5.seconds, 1.seconds, self, Overview)

  val ipAddressCounter = mutable.HashMap.empty[String, Int]

  var messageCounter = 0

  override def receive = {
    case x: String =>
      val senderIp = sender().path.address.host.get
      val count = ipAddressCounter.getOrElse(senderIp, 0)
      ipAddressCounter += (senderIp -> (count + 1))

    case Overview =>
      println("\nOverview: ")

      ipAddressCounter.toSeq.sortBy(_._1).foreach {
        case (ip, count) =>
          println(s"$ip\t-> $count")
      }
    case ProcessMessage =>
      if (messageCounter == 10000) {
        stopProcessing
      } else {
        router ! ""
      }
      messageCounter += 1
    case TerminateSystem =>
      context.system.terminate()
  }

  def stopProcessing: Unit = {
    messageScheduler.cancel()
    overviewScheduler.cancel()
    context.system.scheduler.scheduleOnce(5.seconds, self, Overview)
    router ! PoisonPill
    context.system.scheduler.scheduleOnce(10.seconds, self, TerminateSystem)
  }
}
