package me.tracklist.audio

// Scala mutable collections
import scala.collection.mutable

// Java Audio
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem

// Streams
import java.io.InputStream
import java.io.FileInputStream

class WavWaveform(val filename: String) {

  // May throw IOException or WavFileExecption  
  val file = new java.io.File(filename)
  val wavFile = WavFile.openWavFile(file)

  val validBit = wavFile.getValidBits()
  val numChannels = wavFile.getNumChannels()
  val numFrames = wavFile.getNumFrames()
  val sampleRate = wavFile.getSampleRate()

  val lengthInSeconds : Long = numFrames/sampleRate 
  println("Track is long " + lengthInSeconds + " seconds")

  if (lengthInSeconds == 0) {
    throw new Exception("Track is less than one second long")
  }

  var trackMinutes : Long = lengthInSeconds / 60
  var trackSeconds : Long = lengthInSeconds % 60
  var trackHours : Long = trackMinutes / 60
  trackMinutes = trackMinutes % 60


  println(
    "Track length: %02d:%02d:%02d".format(
      trackHours, 
      trackMinutes, 
      trackSeconds))

  /**
   * Returns the length of the track in seconds
   * \return Double representing the length of the track in seconds
   **/
  def getLengthInSeconds() : Long = {
    return lengthInSeconds
  }


  /**
   * Returns and array of doubles representing the waveform of the track
   * as average of its channels
   * \param pointsPerMinute how many waveform samples we want to keep per minute
   **/
  def getWaveform(pointsPerMinute: Int) : Array[Float] = {

    var waveform = Array[Float]()

    val pointsPerSecond = pointsPerMinute.toFloat/60.toFloat
    // Points of the waveform we will collect
    val totalPoints = Math.ceil(pointsPerSecond*trackSeconds).toInt
    println("Total Points "+totalPoints)
    val sampleSize  = Math.floor(numFrames/totalPoints).toInt
    println("Sample Size "+sampleSize)

    var framesRead  : Int = 0
    val buffer : Array[Double]  = new Array[Double](sampleSize * numChannels);

    do {
      // Read frames into buffer
      framesRead = wavFile.readNormalizedFrames(buffer, sampleSize);
      var frameMax: Float = Float.MinValue
      var frameIndex = 0
      while (frameIndex < sampleSize * numChannels) {
        // we select the maximum in the Frame
        //println(buffer(frameIndex))
        if (buffer(frameIndex) > frameMax) 
          frameMax = buffer(frameIndex).toFloat

        frameIndex = frameIndex + 1
      }
      waveform = waveform :+ frameMax
    } while (framesRead != 0);

    println("Size of the wave form is " + waveform.length)
    return waveform
  }
}

object WavWaveform {
  def apply(filename: String) = new WavWaveform(filename)
  def formatToJson(waveform: Array[Float], precision: Int): String = {
    var waveformString = "[";
    val format : String = "%." + precision + "f"
    for (value <- waveform.slice(0, waveform.length - 1 )) {
      waveformString = waveformString + String.format(format, value: java.lang.Float) + ","
    }
    waveformString = waveformString + String.format(format, waveform(waveform.length-1): java.lang.Float) + "]"
    return waveformString
  }
}