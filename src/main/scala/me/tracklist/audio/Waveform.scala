package me.tracklist.audio

// Scala mutable collections
import scala.collection.mutable

class WavWaveform(val filename: String) {

  // May throw IOException or WavFileExecption  
  val wavFile = WavFile.openWavFile(new java.io.File(filename))
  val validBit = wavFile.getValidBits()
  val numChannels = wavFile.getNumChannels()
  val numFrames = wavFile.getNumFrames()
  val sampleRate = wavFile.getSampleRate()

  val trackSeconds : Double = numFrames.toDouble/sampleRate.toDouble

  if (trackSeconds == 0) {
    throw new Exception("Track is less than one second long")
  }

  println("Track is long " + trackSeconds)
  val trackMinutes : Double = trackSeconds / 60.toDouble

  /**
   * Returns the length of the track in seconds
   * \return Double representing the length of the track in seconds
   **/
  def getLengthInSeconds() : Double = {
    return trackSeconds
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