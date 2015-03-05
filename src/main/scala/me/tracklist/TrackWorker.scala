package me.tracklist

// Akka actor system imports
import akka.actor.{Actor, ActorLogging, Props}

// Application config import
import me.tracklist._

// Entities
import me.tracklist.entities.{Release, Track}

// Scala mutable collections
import scala.collection.mutable

// RabbitMQ imports
import me.tracklist.rabbitmq.RabbitConnector

// Cloudstorage imports
import me.tracklist.cloudstorage.Cloudstorage

// File utils
import me.tracklist.utils.FileUtils

// Audio API
import me.tracklist.audio.Lame
import me.tracklist.audio.Ffmpeg
import me.tracklist.audio.WavWaveform

// Akka futures
import scala.concurrent._
import scala.concurrent.duration._
import ExecutionContext.Implicits.global

// Spray JSON conversion
import spray.json._
import DefaultJsonProtocol._

// DateTime utils (wrapper of Joda time)
import com.github.nscala_time.time.Imports.DateTime

/**
 * Akka actor used to process a release
 * Uses several TrackWorker actors to handle each track
 **/
class TrackWorker extends Actor with ActorLogging {
  import TrackWorker._

  var currentTrack : Track = null
  var currentRelease : Integer = null

  /**
   * Local temporary files
   **/
  var localLosslessPath : String = null
  var mp3CutPath : String = null
  var mp3Path : String = null

  /**
   * Remote uploaded files
   **/
  var remoteMp3CutPath : String = null
  var remoteMp3Path : String = null


  /**
   * Delete local files
   **/
  private def cleanLocal() {
    FileUtils.deleteIfExists(localLosslessPath)
    FileUtils.deleteIfExists(mp3CutPath)
    FileUtils.deleteIfExists(mp3Path)
  }

  /**
   * Delete remote files
   **/
  private def cleanRemote() {
    try {
      Cloudstorage.deleteObject(remoteMp3CutPath)
    } catch {
      case e: Exception => {}
    }
    try {
      Cloudstorage.deleteObject(remoteMp3Path)
    } catch {
      case e: Exception => {}
    }
  }

  def receive = {
    /**
     * The track processing pipeline has to be started
     * 1) Download the lossless track
     * 2) Convert lossless track to mp3 320Kbps
     * 3) Cut the track to half of its length at 160Kbps
     * 4) Find intensity points
     **/
    case TrackMessage(track, releaseId) =>
      currentTrack = track
      currentRelease = releaseId
      log.info("Processing Track " + track.id)
      val filename = FileUtils.nameFromPath(track.path)
      localLosslessPath = FileUtils.localTrackPath(releaseId, filename)

      // Store statistics, better to have them
      var downloadTime : Long = 0;
      var conversionTime : Long = 0;
      var uploadTime: Long = 0;
      var now : Long = System.nanoTime

      // Download lossless track
      Cloudstorage.downloadObject(track.path, localLosslessPath)

      downloadTime = (System.nanoTime - now) / 1000

      // Convert and cut the track
      // TODO manually specify begin-end
      val (baseName, extension) = FileUtils.splitFilename(filename)

      val mp3CutFilename = baseName + "_192_snippet.mp3"
      val mp3Filename = baseName + "_320.mp3"

      mp3CutPath = FileUtils.localTrackPath(releaseId, mp3CutFilename)
      mp3Path = FileUtils.localTrackPath(releaseId, mp3Filename)
      
      val ffmpegCutOptions = Ffmpeg.cutOptions(
        mp3CutPath, 
        192, 0, 30)
      val ffmpegConvertOptions = Ffmpeg.convertOptions(
        mp3Path, 
        320)

      now = System.nanoTime

      val ffmpegConverter = new Ffmpeg(localLosslessPath)
      val conversionFuture1 = Future{ffmpegConverter.convert(ffmpegConvertOptions)}
      val conversionFuture2 = Future{ffmpegConverter.convert(ffmpegCutOptions)}
      var conversionResult1 = 1
      var conversionResult2 = 1

      try {

        var waveformBuilder = new WavWaveform(localLosslessPath);
        val waveform = WavWaveform.formatToJson(waveformBuilder.getWaveform(512), 5)

        remoteMp3CutPath = FileUtils.remoteTrackPath(releaseId, mp3CutFilename)
        remoteMp3Path = FileUtils.remoteTrackPath(releaseId, mp3Filename)

        conversionResult1 = Await.result(conversionFuture1, 1 minutes)
        conversionResult2 = Await.result(conversionFuture2, 1 minutes)


        // If return status is not 0
        if (conversionResult1 != 0 || conversionResult2 != 0) {
          throw new Exception("Coversion failed")
        } else {
          conversionTime = (System.nanoTime - now) / 1000
          // Upload everything
          now = System.nanoTime
          Cloudstorage.uploadObject(
            remoteMp3CutPath, mp3CutPath, "application/octet-stream")
          Cloudstorage.uploadObject(
            remoteMp3Path, mp3Path, "application/octet-stream")
          uploadTime = (System.nanoTime - now) / 1000

        }   

        cleanLocal()

        currentTrack.mp3Path = Some(remoteMp3Path)
        currentTrack.snippetPath = Some(remoteMp3CutPath)
        currentTrack.status = Some("SUCCESS")
        currentTrack.downloadTime = Some(downloadTime)
        currentTrack.conversionTime = Some(conversionTime)
        currentTrack.uploadTime = Some(uploadTime)
        currentTrack.processedAt = Some(DateTime.now.toString)
        currentTrack.waveform = Some(waveform)

        sender ! ReleaseWorker.TrackSuccess(currentTrack)

        //println(waveform.toJson.prettyPrint)
        // sender ! ReleaseWorker.TrackSuccess(currentTrack)
        // sender ! ReleaseWorker.TrackFail(currentTrack)

      } catch {
        case e: me.tracklist.audio.WavFileException => 
          var message = "Waveform extraction for track " + track.id + " failed"
          currentTrack.status = Some("FAIL")
          currentTrack.errorMessage = Some(message)
          println(message)
          // remove the files if they were created
          cleanLocal()
          sender ! ReleaseWorker.TrackFail(currentTrack, message)
        case e: InterruptedException => 
          var message = "Conversion of track " + track.id + " got interrupted"
          currentTrack.status = Some("FAIL")
          currentTrack.errorMessage = Some(message)
          println(message)
          // remove the files if they were created
          cleanLocal()
          sender ! ReleaseWorker.TrackFail(currentTrack, message)
        case e: TimeoutException => 
          var message = "Conversion of track " + track.id + " got timed out"
          currentTrack.status = Some("FAIL")
          currentTrack.errorMessage = Some(message)
          println(message)
          // remove the files if they were created
          cleanLocal()
          sender ! ReleaseWorker.TrackFail(currentTrack, message)
        case e: java.io.IOException =>
          var message = "Upload of track " + track.id + " failed"
          currentTrack.status = Some("FAIL")
          currentTrack.errorMessage = Some(message)
          println(message)
          // remove the files if they were created
          cleanLocal()
          sender ! ReleaseWorker.TrackFail(currentTrack, message)
        case e: Exception =>
          var message = "Processing of track " + track.id + " failed"
          currentTrack.status = Some("FAIL")
          currentTrack.errorMessage = Some(message)
          println(message)
          // remove the files if they were created
          cleanLocal()
          sender ! ReleaseWorker.TrackFail(currentTrack, message)
      }


    case Terminate => 
      context.stop(self)

    case TerminateAndRollback =>
      cleanLocal()
      cleanRemote()
      context.stop(self)

  }

}

object TrackWorker {
  val props = Props[TrackWorker]
  case class TrackMessage(track : Track, releaseId : Integer)
  case object Terminate
  case object TerminateAndRollback
}
