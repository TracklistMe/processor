package me.tracklist.utils

// App configuration
import me.tracklist.ApplicationConfig

// Scala path utils
import scalax.file.Path

// Java file
import java.io.File

/**
 * Utility object for file and paths manipulation 
 **/
object FileUtils {

  /**
   * Checks if a file exists
   **/
  def exists(pathString: String) : Boolean = {
    val path: Path = Path.fromString(pathString)
    return path.exists
  }

  /**
   * Checks if a file exists and is a file
   **/
  def isFile(pathString: String) : Boolean = {
    val path: Path = Path.fromString(pathString)
    return path.exists && path.isFile
  }

  /**
   * Given a filename and a release builds a remote path to the file
   **/
  def remoteTrackPath(releaseId: Int, filename: String) : String =
    "releases/" + releaseId + "/" + filename

  /**
   * Given a filename and a release builds a local path to the file
   **/
  def localTrackPath(releaseId: Int, filename: String) : String =
    ApplicationConfig.LOCAL_STORAGE_PATH + "/" + releaseId + "/" + filename

  /**
   * Given a release id returns the path to it
   **/
  def localReleasePath(releaseId: Int) : String =
    ApplicationConfig.LOCAL_STORAGE_PATH + "/" + releaseId

  /**
   * Create directory if not exists
   **/
  def createDirectory(pathString: String) {
    val path: Path = Path.fromString(pathString)
    path.createDirectory(failIfExists=false)
  }

  /**
   * Deletes a path if exists
   **/
  def deleteIfExists(pathString: String) {
    val path: Path = Path.fromString(pathString)
    path.deleteIfExists()
  }


  /**
   * Deletes a path recursively, continues on fail
   **/
  def deleteRecursively(pathString: String) {
    val path: Path = Path.fromString(pathString)
    path.deleteRecursively(continueOnFailure=true)
  }

  /**
   * Creates a temporary directory for a release
   **/
  def createReleaseDirectory(releaseId: Int) {
    createDirectory(localReleasePath(releaseId))
  }

  /**
   * Recursively deletes the temporary directory for a release
   **/
  def deleteReleaseRecursively(releaseId: Int) {
    deleteRecursively(localReleasePath(releaseId))
  }

  /**
   * Extracts filename from a path
   **/
  def nameFromPath(path : String) : String = {
    return new File(path).getName()
  }

  /**
   * Removes extension from filename
   **/
  def splitFilename(filename: String) : (String,String) = {
    var splitFile = filename.split("\\.(?=[^\\.]+$)")
    if (splitFile.length != 2) throw new Exception()
    else return (splitFile(0), splitFile(1))
  }

}