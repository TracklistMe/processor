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
class Lame(val filename: String) {
  import Lame._

  if (!FileUtils.isFile(filename)) 
    throw new FileNotFoundException("File "+filename+" does not exist")

  /**
   * Calls the command line lame utility with the specified
   * options. Return value is the exit code of the lame binary
   **/
  def convert(options: Options) : Int = {
    val command : String = "lame " + filename + 
      " -V " + options.VBRquality +
      " -b " + options.bitrate + 
      "    " + options.output
    // Operator .! is imported by import scala.sys.process._
    // Blocks until the command is executed returns the status code
    var returnStatus = -1
    options.log match {
      case true => returnStatus = command.!
      case false => returnStatus = command ! NullProcessLogger()
    }
    println("Lame return status code "+returnStatus)
    return returnStatus
  }
}

object Lame {
  
  def apply(filename: String) = new Lame(filename)

  class Options(
    val output: String = "output.mp3", // Output filename
    val bitrate: Int = 128, // Default bitrate is 128
    val VBRquality: Int = 4, // Default variable bitrate quality is 4
    val log: Boolean = false
  ){}

  def options(output: String, bitrate: Int, VBRquality: Int) = 
    new Options(output, bitrate, VBRquality, false)

  def options(output: String, bitrate: Int, VBRquality: Int, log: Boolean) = 
    new Options(output, bitrate, VBRquality, log)
}