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
import me.tracklist.audio.BpmDetector
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
  var oggCutPath : String = null
  var mp3Path : String = null

  /**
   * Remote uploaded files
   **/
  var remoteMp3CutPath : String = null
  var remoteOggCutPath : String = null
  var remoteMp3Path : String = null
  var remoteWaveformPath : String = null

  /**
   * Delete local files
   **/
  private def cleanLocal() {
    if(localLosslessPath != null) FileUtils.deleteIfExists(localLosslessPath)
    if(mp3CutPath != null) FileUtils.deleteIfExists(mp3CutPath)
    if(oggCutPath != null) FileUtils.deleteIfExists(oggCutPath)
    if(mp3Path != null) FileUtils.deleteIfExists(mp3Path)
  }

  /**
   * Delete the actor state
   **/
  private def cleanState() {
    currentTrack = null
    currentRelease = null
    localLosslessPath = null
    mp3CutPath = null
    oggCutPath = null
    mp3Path = null
    remoteMp3CutPath = null
    remoteOggCutPath = null
    remoteMp3Path = null
    remoteWaveformPath = null
  } 

  /**
   * Get snippet begin and end in seconds
   * @param lengthInSeconds length of the track in seconds
   * @returns a pair of the form (cutBegin, cutLength)
   **/
  private def getSnippetRange(lengthInSeconds : Long) : (Long, Long) = {
    val maxSnippetLength : Long = 150L;
    if (maxSnippetLength > lengthInSeconds) {
      return (0, lengthInSeconds)
    } else {
      val snippetCenter : Long = Math.ceil(lengthInSeconds / 2).toLong
      val snippetBegin : Long = snippetCenter - (maxSnippetLength/2)
      return (snippetBegin, maxSnippetLength)
    }
  }

  /**
   * Delete remote files
   **/
  private def cleanRemote() {
    TrackWorker.cleanRemote(currentTrack)
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

      // Clean actor state from previous release
      cleanState()

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

      try {

        // Download lossless track
        Cloudstorage.downloadObject(track.path, localLosslessPath)

        downloadTime = (System.nanoTime - now) / 1000

        // Convert and cut the track
        // TODO manually specify begin-end
        val (baseName, extension) = FileUtils.splitFilename(filename)

        val mp3CutFilename = baseName + "_192_snippet.mp3"
        val oggCutFilename = baseName + "_192_snippet.ogg"
        val mp3Filename = baseName + "_320.mp3"
        val waveformFilename = baseName + ".waveform"

        mp3CutPath = FileUtils.localTrackPath(releaseId, mp3CutFilename)
        oggCutPath = FileUtils.localTrackPath(releaseId, oggCutFilename)
        mp3Path = FileUtils.localTrackPath(releaseId, mp3Filename)

        var waveformBuilder = new WavWaveform(localLosslessPath);
        val lengthInSeconds = waveformBuilder.getLengthInSeconds()
        val snippetRange = getSnippetRange(lengthInSeconds)

        val ffmpegConverter = new Ffmpeg(localLosslessPath)        
        val ffmpegCutOptions = Ffmpeg.cutOptions(
          mp3CutPath, 
          192, snippetRange._1.toInt, snippetRange._2.toInt)
        val ffmpegOggCutOptions = Ffmpeg.cutOptions(
          oggCutPath, 
          192, snippetRange._1.toInt, snippetRange._2.toInt,
          Ffmpeg.VORBIS)
        val ffmpegConvertOptions = Ffmpeg.convertOptions(
          mp3Path, 
          320)

        now = System.nanoTime

        val conversionFuture1 = Future{ffmpegConverter.convert(ffmpegConvertOptions)}
        val conversionFuture2 = Future{ffmpegConverter.convert(ffmpegCutOptions)}
        val conversionFuture3 = Future{ffmpegConverter.convert(ffmpegOggCutOptions)}
        var conversionResult1 = 1
        var conversionResult2 = 1
        var conversionResult3 = 1

        
        val waveform = WavWaveform.formatToJson(waveformBuilder.getWaveform(512), 2)

        remoteMp3CutPath = FileUtils.remoteTrackPath(releaseId, mp3CutFilename)
        remoteOggCutPath = FileUtils.remoteTrackPath(releaseId, oggCutFilename)
        remoteMp3Path = FileUtils.remoteTrackPath(releaseId, mp3Filename)
        remoteWaveformPath = FileUtils.remoteTrackPath(releaseId, waveformFilename)

        conversionResult1 = Await.result(conversionFuture1, 1 minutes)
        conversionResult2 = Await.result(conversionFuture2, 1 minutes)
        conversionResult3 = Await.result(conversionFuture3, 1 minutes)


        // If return status is not 0
        if (conversionResult1 != 0 || conversionResult2 != 0 || conversionResult3 != 0) {
          throw new Exception("Coversion failed")
        } else {
          conversionTime = (System.nanoTime - now) / 1000
          // Upload everything
          now = System.nanoTime
          // Upload waveform
          Cloudstorage.uploadObjectAsByteArray(remoteWaveformPath,
            waveform, "application/json")
          // Upload 192 Kbps mp3 snippet
          Cloudstorage.uploadObject(
            remoteMp3CutPath, mp3CutPath, "application/octet-stream")
          // Upload 192 Kbps ogg snippet
          Cloudstorage.uploadObject(
            remoteOggCutPath, mp3CutPath, "application/octet-stream")
          // Upload 320 Kbps mp3
          Cloudstorage.uploadObject(
            remoteMp3Path, mp3Path, "application/octet-stream")
          uploadTime = (System.nanoTime - now) / 1000

        }   

        val (bpmResult, bpmString) = BpmDetector(localLosslessPath).bpm()
        if (bpmResult != 0) {
          throw new Exception("Bpm detection failed " + bpmString)
        }

        cleanLocal()

        currentTrack.mp3Path = Some(remoteMp3Path)
        currentTrack.snippetPath = Some(remoteMp3CutPath)
        currentTrack.oggSnippetPath = Some(remoteOggCutPath)
        currentTrack.status = Some("PROCESSING_SUCCEEDED")
        currentTrack.downloadTime = Some(downloadTime)
        currentTrack.conversionTime = Some(conversionTime)
        currentTrack.uploadTime = Some(uploadTime)
        currentTrack.processedAt = Some(DateTime.now.toString)
        currentTrack.lengthInSeconds = Some(lengthInSeconds)
        currentTrack.waveform = Some(remoteWaveformPath)
        currentTrack.bpm = Some(bpmString.toDouble)

        sender ! ReleaseWorker.TrackSuccess(currentTrack)

        //println(waveform.toJson.prettyPrint)
        // sender ! ReleaseWorker.TrackSuccess(currentTrack)
        // sender ! ReleaseWorker.TrackFail(currentTrack)

      } catch {
        case e: me.tracklist.audio.WavFileException => 
          var exceptionMessage = e.getMessage()
          var message = "Track " + track.id + " error: " + exceptionMessage
          currentTrack.status = Some("PROCESSING_FAILED")
          currentTrack.errorMessage = Some(exceptionMessage)
          log.info(message)
          // remove the files if they were created
          cleanLocal()
          sender ! ReleaseWorker.TrackFail(currentTrack, message)
        case e: InterruptedException => 
          var exceptionMessage = e.getMessage()
          var message = "Track " + track.id + " error: " + exceptionMessage
          currentTrack.status = Some("PROCESSING_FAILED")
          currentTrack.errorMessage = Some(exceptionMessage)
          log.info(message)
          // remove the files if they were created
          cleanLocal()
          sender ! ReleaseWorker.TrackFail(currentTrack, message)
        case e: TimeoutException => 
          var exceptionMessage = e.getMessage()
          var message = "Track " + track.id + " error: " + exceptionMessage
          currentTrack.status = Some("PROCESSING_FAILED")
          currentTrack.errorMessage = Some(exceptionMessage)
          log.info(message)
          // remove the files if they were created
          cleanLocal()
          sender ! ReleaseWorker.TrackFail(currentTrack, message)
        case e: java.io.IOException =>
          var exceptionMessage = e.getMessage()
          var message = "Track " + track.id + " error: " + exceptionMessage
          currentTrack.status = Some("PROCESSING_FAILED")
          currentTrack.errorMessage = Some(exceptionMessage)
          log.info(message)
          // remove the files if they were created
          cleanLocal()
          cleanRemote()
          sender ! ReleaseWorker.TrackFail(currentTrack, message)
        case e: Exception =>
          var exceptionMessage = e.getMessage()
          var message = "Track " + track.id + " error: " + exceptionMessage
          currentTrack.status = Some("PROCESSING_FAILED")
          currentTrack.errorMessage = Some(exceptionMessage)
          log.info(message)
          // remove the files if they were created
          cleanLocal()
          cleanRemote()
          sender ! ReleaseWorker.TrackFail(currentTrack, message)
      }


    case Terminate => 
      context.stop(self)

    case TerminateAndRollback =>
      cleanLocal()
      cleanRemote()
      context.stop(self)

    case Rollback =>
      cleanLocal()
      cleanRemote()

  }

}

object TrackWorker {
  val props = Props[TrackWorker]
  case class TrackMessage(track : Track, releaseId : Integer)
  case object Terminate
  case object Rollback
  case object TerminateAndRollback

  def cleanRemote(track : Track) {
    try {
      if(track.mp3Path != None) Cloudstorage.deleteObject(track.mp3Path.get)
    } catch {
      case e: Exception => {}
    }
    try {
      if(track.snippetPath != None) Cloudstorage.deleteObject(track.snippetPath.get)
    } catch {
      case e: Exception => {}
    }
    try {
      if(track.oggSnippetPath != None) Cloudstorage.deleteObject(track.oggSnippetPath.get)
    } catch {
      case e: Exception => {}
    }
    try {
      if(track.waveform != None) Cloudstorage.deleteObject(track.waveform.get)
    } catch {
      case e: Exception => {}
    }
  }
}
