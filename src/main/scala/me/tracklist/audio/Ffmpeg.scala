package me.tracklist.audio

// File utils
import me.tracklist.utils.{FileUtils, NullProcessLogger}

// Java FileNotFoundException
import java.io.FileNotFoundException

// Scala processor of external commands
import scala.sys.process._

/**
 * Utility class to apply audio conversions
 * Constructor throws FileNotFoundException if the file does not exist
 **/
class Ffmpeg(val filename: String) {
  import Ffmpeg._

  if (!FileUtils.isFile(filename)) 
    throw new FileNotFoundException("File "+filename+" does not exist")

  /**
   * Calls the command line ffmpeg utility with the specified
   * options. Return value is the exit code of the lame binary
   **/
  def convert(options: Options) : Int = {

    var command = "ffmpeg" +
      " -i "    + filename + 
      " -y "    +
      " -b:a "  + options.bitrate + "k"

    if (options.duration > 0 && options.start >= 0) {

      val hoursStart        = (options.start / 3600).floor.toInt
      val minutesStart      = ((options.start - hoursStart*3600) / 60).floor.toInt
      val secondsStart      = options.start % 60

      val hoursDuration     = (options.duration / 3600).floor.toInt
      val minutesDuration   = ((options.duration - hoursDuration*3600) / 60).floor.toInt
      val secondsDuration   = options.duration % 60     

      command = command + 
        " -ss " + hoursStart + ":" + minutesStart + ":" + secondsStart +
        " -t "  + hoursDuration + ":" + minutesDuration + ":" + secondsDuration
    }

    if (options.codec == Ffmpeg.VORBIS) {
      command = command + " -acodec libvorbis"
    }

    command = command + " " + options.output

    println(command)

    // Operator .! is imported by import scala.sys.process._
    // Blocks until the command is executed returns the status code
    var returnStatus = -1
    options.log match {
      case true => returnStatus = command.!
      case false => returnStatus = command ! NullProcessLogger()
    }
    println("ffmpeg return status code "+returnStatus)
    return returnStatus
  }
}

object Ffmpeg {

  val MP3     = 0
  val VORBIS  = 1
  
  def apply(filename: String) = new Ffmpeg(filename)

  class Options(
    val output: String = "output.mp3", // Output filename
    val bitrate: Int = 128, // Default bitrate is 128
    val start: Int = 0, // Cut start point in seconds
    val duration: Int = 30, // Duration of the cut in seconds
    val codec : Int = MP3, // Codec used to compress audio data
    val log: Boolean = false // whether log ffmpeg to stdout or not
  ){}

  def cutOptions(output: String, bitrate: Int, start: Int, duration: Int) = 
    new Options(output, bitrate, start, duration, MP3, false)

  def cutOptions(output: String, bitrate: Int, start: Int, duration: Int, codec: Int) = 
    new Options(output, bitrate, start, duration, codec, false)

  def cutOptions(output: String, bitrate: Int, start: Int, duration: Int, log: Boolean) = 
    new Options(output, bitrate, start, duration, MP3, log)

  def cutOptions(output: String, bitrate: Int, start: Int, duration: Int, codec: Int, log: Boolean) = 
    new Options(output, bitrate, start, duration, codec, log)

  def convertOptions(output: String, bitrate: Int) = 
    new Options(output, bitrate, 0, 0, MP3, false)

  def convertOptions(output: String, bitrate: Int, start: Int, duration: Int, log: Boolean) = 
    new Options(output, bitrate, 0, 0, MP3, log)
}