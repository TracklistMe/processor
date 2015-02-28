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

  println("Track is long " + trackSeconds)
  val trackMinutes : Double = trackSeconds / 60.toDouble

  /**
   * Returns and array of doubles representing the waveform of the track
   * as average of its channels
   * \param pointsPerMinute how many waveform samples we want to keep per minute
   **/
  def getWaveform(pointsPerMinute: Int) : Array[Float] = {

    var waveform = Array[Float]()

    val totalPoints = Math.ceil(pointsPerMinute*trackMinutes).toInt
    println("Total Points "+totalPoints)
    val sampleSize  = Math.floor(numFrames/totalPoints).toInt
    println("Sample Size "+sampleSize)

    var framesRead  : Int = 0
    val buffer : Array[Double]  = new Array[Double](sampleSize * numChannels);

    do {
      // Read frames into buffer
      framesRead = wavFile.readFrames(buffer, sampleSize);
      var frameMax: Float = Float.MinValue
      var frameIndex = 0
      while (frameIndex < sampleSize * numChannels) {
        // we select the maximum in the Frame
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
}