package me.tracklist.audio

import java.io.File

// Java Audio
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioFormat

// Streams
import java.io.InputStream
import java.io.FileInputStream
import java.io.BufferedInputStream

// Non standard, should be avoided?
import com.sun.media.sound.AiffFileReader

class AiffFile(val filename: String) {

  /**
   * Constructor may throw IOException
   **/
  val fileReader = new AiffFileReader()
  val file = new File(filename)
  if (file.exists())
    println("File actually exists: " + file.getAbsolutePath())

  val audioFileFormat = fileReader.getAudioFileFormat(file)
  val audioFormat = audioFileFormat.getFormat

  println("Frame length         %d".format(audioFileFormat.getFrameLength()))
  println("Song duration       %d".format(audioFileFormat.getProperty("duration").asInstanceOf[Long])) //in microseconds
  println("Frame rate           %f".format(audioFormat.getFrameRate()))
  println("Frame size in bytes  %d".format(audioFormat.getFrameSize()))
  println("Channels             %d".format(audioFormat.getChannels()))

  /**
   * Audio format information
   **/
  val numFrames = audioFileFormat.getFrameLength()
  val frameBytes = audioFormat.getFrameSize()
  val validBits = frameBytes * 8
  val numChannels = audioFormat.getChannels()
  val frameRate = audioFormat.getFrameRate()
  val encoding : AudioFormat.Encoding = audioFormat.getEncoding()

  encoding match {
    case AudioFormat.Encoding.ALAW => println("Encoding is ALAW")
    //case AudioFormat.Encoding.PCM_FLOAT => println("Encoding is floating point PCM")
    case AudioFormat.Encoding.PCM_SIGNED => println("Encoding is signed PCM")
    case AudioFormat.Encoding.PCM_UNSIGNED => println("Encoding is unsigned PCM")
    case AudioFormat.Encoding.ULAW => println("Encoding is ULAW")
  }

  /**
   * Maximum and minumum values for PCM data
   * And values needed to convert signed PCM to unsigned pcm
   **/
  var maxPCMValue : Long = 0;
  var minPCMValue : Long = 0;
  var signedToUnsigned : Long = 0;
  var maxUnsignedPCMValue : Long = 0;

  encoding match {
    case AudioFormat.Encoding.PCM_SIGNED => 
      maxPCMValue = (0x1 << (validBits-1)) - 1
      minPCMValue = - maxPCMValue - 1
      signedToUnsigned = maxPCMValue + 1
      maxUnsignedPCMValue = (0x1 << validBits) - 1
    case AudioFormat.Encoding.PCM_UNSIGNED => 
      maxPCMValue = (0x1 << validBits) - 1
      minPCMValue = 0
      signedToUnsigned = 0
      maxUnsignedPCMValue = maxPCMValue
  }


  /**
   * Track information
   **/
  val trackMicroseconds = audioFileFormat.getProperty("duration").asInstanceOf[Long]
  val trackSeconds : Double = trackMicroseconds.toDouble / 1000.toDouble
  val trackMinutes : Double = trackSeconds / 60.toDouble

  if (trackSeconds == 0) {
    //throw new Exception("Track is less than one second long")
  }

  /**
   * We use a buffered input stream as it should be much faster
   **/
  val fileInputStream       = new FileInputStream(file)
  val bufferedImputStream   = new BufferedInputStream(fileInputStream)
  val audioStream           = fileReader.getAudioInputStream(bufferedImputStream)
  //val audioFormat = stream.getFormat()

  /**
   * Returns and array of doubles representing the waveform of the track
   * as average of its channels
   * \param pointsPerMinute how many waveform samples we want to keep per minute
   **/
  def getWaveform(pointsPerMinute: Int) : Array[Float] = {

    var waveform = Array[Float]()

    val pointsPerSecond = pointsPerMinute/60
    // Points of the waveform we will collect
    val totalPoints = Math.ceil(pointsPerSecond*trackSeconds).toInt
    println("Total Points "+totalPoints)
    val sampleSize  = Math.floor(numFrames/totalPoints).toInt
    println("Sample Size "+sampleSize)


    var framesRead  : Int = 0
    val buffer : Array[Double]  = new Array[Double](sampleSize * numChannels);

    do {
      // Read frames into buffer
      framesRead = readNormalizedFrames(buffer, sampleSize);
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

  def readFrames(sampleBuffer: Array[Int], numFramesToRead: Int) : Int = {
    return readFrames(sampleBuffer, 0, numFramesToRead);
  }

  def readFrames(sampleBuffer: Array[Int], offset: Int, numFramesToRead: Int) : Int = {

    val bytesToRead = numFramesToRead*frameBytes
    val byteBuffer = new Array[Byte](bytesToRead);

    val byteRead = audioStream.read(byteBuffer, 0, bytesToRead);
    var byteIndex = 0

    var frameIndex = 0;

    while (frameIndex < numFramesToRead && byteIndex + frameBytes - 1 < byteRead) {
      var frameValue = 0;
      for (i <- 0 to frameBytes-1 ) {
        frameValue = (frameValue << 8) | byteBuffer(byteIndex + i)
      }
      sampleBuffer(frameIndex) = frameValue
      byteIndex = byteIndex + frameBytes
      frameIndex = frameIndex + 1
    }

    return frameIndex
  }

  /**
   * Returns frames values as if the were unsigned PCM format
    * If original data are signed PCM they are converted
   **/
  def readUnsignedFrames(sampleBuffer: Array[Int], numFramesToRead: Int) : Int = {
    return readUnsignedFrames(sampleBuffer, 0, numFramesToRead);
  }

  def readUnsignedFrames(sampleBuffer: Array[Int], offset: Int, numFramesToRead: Int) : Int = {

    val bytesToRead = numFramesToRead*frameBytes
    val byteBuffer = new Array[Byte](bytesToRead);

    val byteRead = audioStream.read(byteBuffer, 0, bytesToRead);
    var byteIndex = 0

    var frameIndex = 0;

    while (frameIndex < numFramesToRead && byteIndex + frameBytes - 1 < byteRead) {
      var frameValue = 0;
      for (i <- 0 to frameBytes-1 ) {
        frameValue = (frameValue << 8) | byteBuffer(byteIndex + i)
      }
      sampleBuffer(frameIndex) = (frameValue+signedToUnsigned.toInt)
      byteIndex = byteIndex + frameBytes
      frameIndex = frameIndex + 1
    }

    return frameIndex
  }

  def readFrames(sampleBuffer: Array[Double], numFramesToRead: Int) : Int = {
    return readFrames(sampleBuffer, 0, numFramesToRead);
  }

  def readFrames(sampleBuffer: Array[Double], offset: Int, numFramesToRead: Int) : Int = {

    val bytesToRead = numFramesToRead*frameBytes
    val byteBuffer = new Array[Byte](bytesToRead);

    val byteRead = audioStream.read(byteBuffer, 0, bytesToRead);
    var byteIndex = 0

    var frameIndex = 0;

    while (frameIndex < numFramesToRead && byteIndex + frameBytes - 1 < byteRead) {
      var frameValue = 0;
      for (i <- 0 to frameBytes-1 ) {
        frameValue = (frameValue << 8) | byteBuffer(byteIndex + i)
      }
      sampleBuffer(frameIndex) = frameValue.toDouble / maxPCMValue.toDouble
      byteIndex = byteIndex + frameBytes
      frameIndex = frameIndex + 1
    }

    return frameIndex
  }

  def readNormalizedFrames(sampleBuffer: Array[Double], numFramesToRead: Int) : Int = {
    return readNormalizedFrames(sampleBuffer, 0, numFramesToRead);
  }

  def readNormalizedFrames(sampleBuffer: Array[Double], offset: Int, numFramesToRead: Int) : Int = {

    val bytesToRead = numFramesToRead*frameBytes
    val byteBuffer = new Array[Byte](bytesToRead);

    val byteRead = audioStream.read(byteBuffer, 0, bytesToRead);
    var byteIndex = 0

    var frameIndex = 0;

    while (frameIndex < numFramesToRead && byteIndex + frameBytes - 1 < byteRead) {
      var frameValue = 0;
      for (i <- 0 to frameBytes-1 ) {
        frameValue = (frameValue << 8) | byteBuffer(byteIndex + i)
      }
      sampleBuffer(frameIndex) = (frameValue+signedToUnsigned.toInt).toDouble / maxUnsignedPCMValue.toDouble
      byteIndex = byteIndex + frameBytes
      frameIndex = frameIndex + 1
    }

    return frameIndex
  }

}

object AiffFile {

}