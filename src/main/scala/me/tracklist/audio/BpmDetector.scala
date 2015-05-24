package me.tracklist.audio

// File utils
import me.tracklist.utils.{FileUtils, NullProcessLogger}

// Java FileNotFoundException
import java.io.FileNotFoundException

// Scala processor of external commands
import scala.sys.process._

import me.tracklist.ApplicationConfig

/**
 * Utility class to compute tempo in BPM for an audio file
 **/
class BpmDetector(val filename: String) {

  if (!FileUtils.isFile(filename)) 
    throw new FileNotFoundException("File " + filename + " does not exist")

  /**
   * Calls the python bpm detector utility with the specified
   * options. 
   **/
  def bpm() : (Int, String) = {

    var command = "python " + BpmDetector.python_script +" --filename "    + filename

    // Operator .! is imported by import scala.sys.process._
    // Blocks until the command is executed returns the status code
    var returnStatus = -1
    val buffer = new StringBuffer()
    returnStatus = command ! ProcessLogger(buffer append _)
    
    return (returnStatus, buffer.toString)
  }
}

object BpmDetector {

  private val python_script = ApplicationConfig.PYTHON_PATH + "bpm.py"

  def apply(filename: String) = new BpmDetector(filename)
}
