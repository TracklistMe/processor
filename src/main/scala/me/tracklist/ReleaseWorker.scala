package me.tracklist

// Akka actor system imports
import akka.actor.{Actor, ActorLogging, Props, ActorRef}

// Application config import
import me.tracklist._

// Entities
import me.tracklist.entities._
import me.tracklist.entities.TracklistJsonProtocol._

//Json 
import spray.json._

// Scala mutable collections
import scala.collection.mutable

// RabbitMQ imports
import me.tracklist.rabbitmq.RabbitConnector

// File utils
import me.tracklist.utils.FileUtils

// DateTime utils (wrapper of Joda time)
import com.github.nscala_time.time.Imports.DateTime

/**
 * Akka actor used to process a release
 * Uses several TrackWorker actors to handle each track
 **/
class ReleaseWorker extends Actor with ActorLogging {
  import ReleaseWorker._

  /**
   * Release being processed
   **/
  var currentRelease : Release = null

  /**
   * Time at which currentRelease has been received
   **/
  var receivedAt : Long = 0L

  /**
   * Tracks status
   **/
  var tracksStatus : mutable.HashMap[Int, TrackStatus] = mutable.HashMap[Int, TrackStatus]()

  /**
   * Track workers
   **/
  var trackWorkers = Vector.fill(ApplicationConfig.TRACK_WORKERS) {
      val worker = context.actorOf(Props[TrackWorker])
      context watch worker
      worker
    }

  /**
   * Number of tracks that have already been processed
   **/
  var processedTracks : Integer = 0

  /**
   * Next track to process
   **/
  var nextTrackToProcess : Integer = 0

  /**
   * True only if one of the tracks in the released failed processing
   **/
  var releaseFailed = false

  /**
   * Number of workers available
   **/ 
  var availableWorkers = ApplicationConfig.TRACK_WORKERS

  /**
   * Queue with the releases to be processed
   **/
  val releaseQueue = RabbitConnector(
    ApplicationConfig.RABBITMQ_HOST,
    ApplicationConfig.RABBITMQ_PORT,
    ApplicationConfig.RABBITMQ_USERNAME,
    ApplicationConfig.RABBITMQ_PASSWORD,
    ApplicationConfig.RABBITMQ_RELEASE_QUEUE,
    RabbitConnector.DURABLE)

  /**
   * Queue where to public results
   **/
  val resultQueue = RabbitConnector(
    ApplicationConfig.RABBITMQ_HOST,
    ApplicationConfig.RABBITMQ_PORT,
    ApplicationConfig.RABBITMQ_USERNAME,
    ApplicationConfig.RABBITMQ_PASSWORD,
    ApplicationConfig.RABBITMQ_RESULT_QUEUE,
    RabbitConnector.DURABLE)

  private def clear() = {
    receivedAt = 0
    releaseFailed = false;
    processedTracks = 0;
    tracksStatus.clear();
    currentRelease = null;

    nextTrackToProcess = 0
    availableWorkers = ApplicationConfig.TRACK_WORKERS
  }

  private def rollbackRelease() {
    currentRelease.Tracks.foreach({track => 
      if (tracksStatus.get(track.id) == TrackStatus.Success) {
        TrackWorker.cleanRemote(track)
      }
    })
  }

  private def sendMessageAndClear() {
    try {
      resultQueue.blockingPublish(
        currentRelease.toJson.prettyPrint, 
        "application/json", 
        true)
    } catch {
      case e: Exception => log.info(e.getMessage())
    } finally {
      FileUtils.deleteReleaseRecursively(currentRelease.id)
      clear();
    } 
  }

  def receive = {
    /**
     * When the consume message is received we need to consume
     * a message, we do it asynchronously. When a message is read 
     * it is forwarded to self as a ReleaseMessage
     **/
    case Consume =>
      releaseQueue.nonBlockingConsume({
        messageString: String => 
          self ! ReleaseMessage(messageString)
        })
    /**
     * When a release message is received it has to be parsed
     * then TrackWorkers have to be instructed to process tracks 
     **/
    case ReleaseMessage(releaseString) => 
      try {
        // 1) Parse release
        // 2) Store release as currentRelease
        currentRelease = releaseString.parseJson.convertTo[Release]
        receivedAt = System.nanoTime
        log.info("Received release with id = " + currentRelease.id)
        // 3) Create a directory to store temporary release data
        FileUtils.createReleaseDirectory(currentRelease.id)
        // 4) Populate tracks, set status to Processing
        // 5) Start a track worker for each track
        while (nextTrackToProcess < ApplicationConfig.TRACK_WORKERS && 
          nextTrackToProcess < currentRelease.Tracks.length) {          
          val track = currentRelease.Tracks(nextTrackToProcess)
          trackWorkers(nextTrackToProcess) ! TrackWorker.TrackMessage(track, currentRelease.id)
          tracksStatus.put(track.id, TrackStatus.Processing)
          availableWorkers = availableWorkers - 1
          nextTrackToProcess = nextTrackToProcess + 1
        }
      } catch {
        case e: Exception => 
          log.info("Received malformed release message")
          // We consume a new Release
          self ! Consume
      }

    /**
     * A release track has be processed correctly
     **/
    case TrackSuccess(track) =>
      log.info("Track " + track.id + " processed correctly")
      tracksStatus.put(track.id, TrackStatus.Success)
      processedTracks = processedTracks + 1
      availableWorkers = availableWorkers + 1

      log.info("Processed " + processedTracks + " tracks out of " + currentRelease.Tracks.length)
      if (!releaseFailed) {
        if (processedTracks == currentRelease.Tracks.length) {
          // If we processed all the tracks
          log.info("Release " + currentRelease.id + " processed correctly")
          currentRelease.status = Some("PROCESSED")
          currentRelease.processedAt = Some(DateTime.now.toString)
          currentRelease.processingTime = Some((System.nanoTime - receivedAt)/1000)  
          sendMessageAndClear()
          self ! Consume          
        } else {
          if (nextTrackToProcess < currentRelease.Tracks.length) {
            // If there are still tracks to be processed
            sender ! TrackWorker.TrackMessage(currentRelease.Tracks(nextTrackToProcess), currentRelease.id)
            availableWorkers = availableWorkers - 1
            nextTrackToProcess = nextTrackToProcess + 1
          }
        } 
      } else {
        if (availableWorkers == ApplicationConfig.TRACK_WORKERS) {
          log.info("Release " + currentRelease.id + " processing failed")

          rollbackRelease()
          currentRelease.status = Some("PROCESSING_FAILED")
          sendMessageAndClear()
          self ! Consume
        }
      }

    /**
     * A release track processing has failed
     **/  
    case TrackFail(track, message) => 
      log.info("Track " + track.id + " processing failed")
      tracksStatus.put(track.id, TrackStatus.Fail)
      releaseFailed = true
      availableWorkers = availableWorkers + 1

      if (availableWorkers == ApplicationConfig.TRACK_WORKERS) {
        log.info("Release " + currentRelease.id + " processing failed")

        rollbackRelease()
        currentRelease.status = Some("PROCESSING_FAILED")
        sendMessageAndClear()
        self ! Consume
      }
  }
}

object ReleaseWorker {
  val props = Props[ReleaseWorker]
  case class ReleaseMessage(releaseJson : String)
  case object Consume
  case class TrackSuccess(track: Track)
  case class TrackFail(track: Track, message: String)
}