package me.tracklist

import akka.actor.{Actor, ActorLogging, Props, Terminated}

class ReleaseBroker extends Actor with ActorLogging {
  import ReleaseBroker._

  var releaseWorkers = Vector.fill(5) {
      val worker = context.actorOf(Props[ReleaseWorker])
      context watch worker
      worker
    }

  def receive = {
    case Initialize => 
      log.info("Initializing release workers")
      // Instruct each worker to consume from release queue
      releaseWorkers.map(_ ! ReleaseWorker.Consume)

    case Terminated(terminated) =>
      val worker = context.actorOf(Props[ReleaseWorker])
      context watch worker
      // We remove the terminated actor and add a new one
      releaseWorkers = (releaseWorkers diff Vector(terminated)) :+ worker 
      // Instruct the new worker to consume from release queue
      worker ! ReleaseWorker.Consume
  } 

}

object ReleaseBroker {
  val props = Props[ReleaseBroker]
  case object Initialize
}