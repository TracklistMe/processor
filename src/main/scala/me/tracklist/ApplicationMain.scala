package me.tracklist

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import akka.event.Logging
import scalax.io._

// Akka futures
import scala.concurrent._
import scala.concurrent.duration._
import ExecutionContext.Implicits.global

// Cloudstorage API
import me.tracklist.cloudstorage.Cloudstorage
import me.tracklist.rabbitmq._

// Audio API
import me.tracklist.audio.Lame
import me.tracklist.audio.Ffmpeg
import me.tracklist.audio._


// File utils
import me.tracklist.utils.FileUtils

// Entities
import me.tracklist.entities._
import me.tracklist.entities.TracklistJsonProtocol._

//Json 
import spray.json._

object ApplicationMain extends App {

  //var waveform = new WavWaveform("storage/SPH139_1.wav")
  //println("VALID BITS ARE " + waveform.validBit)
  //waveform.getWaveform(512).foreach(x => print(x + ", "))
  //val file = new AiffFile("wood12.aiff")
  //file.getWaveform(1024).foreach(x => println(x + ", "))
  //val buffer = new Array[Double](100)
/*
  var count = file.readFrames(buffer, 100)
  println("=== SIGNED PCM FIRST "+count+" FRAMES ===")
  buffer.foreach(x => print(x+", "))

  val file2 = new AiffFile("wood12.aiff")

  count = file2.readNormalizedFrames(buffer, 100)
  println("=== UNSIGNED PCM FIRST "+count+" FRAMES ===")
  buffer.foreach(x => print(x+", "))
*/
  //Cloudstorage.uploadObjectAsByteArray("file.txt",
  //          "{\"Hello\": \"World2\"}", "application/json")

/*
  val converter = new Lame("storage/1/SPH139_1.wav")
  val options320 = Lame.options(
    "storage/1/SPH139_1_320.mp3", 
    320,
    2)

  val options192 = Lame.options(
    "storage/1/SPH139_1_192.mp3", 
    192,
    2)

  val ffmpegCutOptions = Ffmpeg.cutOptions(
    "storage/1/SPH139_1_192_sample.mp3",
    192,
    0,
    30)

  val ffmpegConvertOptions = Ffmpeg.convertOptions(
    "storage/1/SPH139_1_320.mp3",
    320)

  val ffmpegConverter = new Ffmpeg("storage/1/SPH139_1.wav")

  var waveformBuilder = new WavWaveform("storage/1/SPH139_1.wav");
  var waveform = waveformBuilder.getWaveform(1024)
  //println(waveform.toJson.prettyPrint)


  //Cloudstorage.deleteObject("file.txt")

  val (name, extension) = FileUtils.splitFilename("SPH139_1.wav")
  println("Read file "+name+" with extension "+extension)

  println("Sequential conversion time (microseconds)")
*/
  /** TEST LAME CONVERSION AND FFMPEG CUTTING 
  for (i <- 1 to 1) {
    val now = System.nanoTime
    val conversionFuture1 = Future{ffmpegConverter.convert(ffmpegConvertOptions)}
    val conversionFuture2 = Future{ffmpegConverter.convert(ffmpegCutOptions)}

    var conversionResult1 = -1
    var conversionResult2 = -1

    // Throws timeout exception!!!
    try {
      conversionResult1 = Await.result(conversionFuture1, 1 minutes)
      conversionResult2 = Await.result(conversionFuture2, 1 minutes)
    } catch {
      case e: Exception => 
        println("Fatal error occurred while converting track")
        // TODO remove the files if they were created
    }
    val micros = (System.nanoTime - now) / 1000
    println("%d".format(micros))
  }
**/

/** TEST LAME CONVERSION SPEED 
  for (i <- 1 to 1) {
    val now = System.nanoTime
    val conversionFuture1 = Future{converter.convert(options320)}
    val conversionFuture2 = Future{converter.convert(options192)}

    var conversionResult1 = -1
    var conversionResult2 = -1

    // Throws timeout exception!!!
    try {
      conversionResult1 = Await.result(conversionFuture1, 1 minutes)
      conversionResult2 = Await.result(conversionFuture2, 1 minutes)
    } catch {
      case e: Exception => 
        println("Fatal error occurred while converting track")
        // TODO remove the files if they were created
    }
    val micros = (System.nanoTime - now) / 1000
    println("%d".format(micros))
  }
**/
/**
  println("Sequential conversion time (microseconds)")

  for (i <- 1 to 10) {
    val now = System.nanoTime
    converter.convert(options320)
    converter.convert(options192)
    val micros = (System.nanoTime - now) / 1000
    println("%d".format(micros))
  }
**/


  //FileUtils.deleteRecursively(ApplicationConfig.LOCAL_STORAGE_PATH)
  //FileUtils.createDirectory(ApplicationConfig.LOCAL_STORAGE_PATH)
  //FileUtils.createReleaseDirectory(1)

  // val connector = RabbitConnector(
  //   ApplicationConfig.RABBITMQ_HOST,
  //   ApplicationConfig.RABBITMQ_PORT,
  //   ApplicationConfig.RABBITMQ_USERNAME,
  //   ApplicationConfig.RABBITMQ_PASSWORD,
  //   ApplicationConfig.RABBITMQ_RELEASE_QUEUE,
  //   RabbitConnector.DURABLE)

  // connector.blockingPublish("{\"json\": \"whatever1\"}", "application/json", true)
  // connector.blockingPublish("{\"json\": \"whatever2\"}", "application/json", true)
  // connector.blockingPublish("{\"json\": \"whatever3\"}", "application/json", true)
  // connector.blockingPublish("{\"json\": \"whatever4\"}", "application/json", true)
  // connector.blockingPublish("{\"json\": \"whatever5\"}", "application/json", true)

  /**
  Cloudstorage.listAndPrint
  Cloudstorage.downloadObject("file.txt", "storage/file.txt")
  Cloudstorage.uploadObject("someFile.txt", "../someFile.txt", "application/octet-stream")

  val output:Output = Resource.fromFile("../someFile.txt")
  output.write("gigiogianni")(Codec.UTF8)

  
  val log = Logging.getLogger(system, this)
  
  val conf = ConfigFactory.load();
  log.info(ApplicationConfig.RABBITMQ_HOST);
  log.info(""+ApplicationConfig.RELEASE_WORKERS);
  **/
  
  val system = ActorSystem("tracklistme")

  // val releaseActor = system.actorOf(ReleaseWorker.props, "releaseActor")
  val releaseBroker = system.actorOf(ReleaseBroker.props, "releaseBroker")
  releaseBroker ! ReleaseBroker.Initialize
  // val pingActor = system.actorOf(PingActor.props, "pingActor")
  
  // releaseActor ! ReleaseWorker.ReleaseMessage("CIAO MAMMA")

  // pingActor ! PingActor.Initialize
  // This example app will ping pong 3 times and thereafter terminate the ActorSystem - 
  // see counter logic in PingActor
  system.awaitTermination()
}


object ApplicationConfig {
  private val conf = ConfigFactory.load();

  val GOOGLE_DEVELOPER_EMAIL      = conf.getString("google.developer_email")
  val GOOGLE_DEVELOPER_KEY_PATH   = conf.getString("google.developer_key_path")
  val GOOGLE_PROJECT_NAME         = conf.getString("google.project_name")
  val GOOGLE_BUCKET_NAME          = conf.getString("google.bucket_name")

  val RABBITMQ_HOST               = conf.getString("rabbitmq.host")
  val RABBITMQ_PORT               = conf.getInt("rabbitmq.port")
  val RABBITMQ_USERNAME           = conf.getString("rabbitmq.username")
  val RABBITMQ_PASSWORD           = conf.getString("rabbitmq.password")
  val RABBITMQ_RELEASE_QUEUE      = conf.getString("rabbitmq.release_queue")
  val RABBITMQ_RESULT_QUEUE       = conf.getString("rabbitmq.result_queue")
  
  val RELEASE_WORKERS             = conf.getInt("release_workers")

  val LOCAL_STORAGE_PATH          = conf.getString("local_storage_path")

}