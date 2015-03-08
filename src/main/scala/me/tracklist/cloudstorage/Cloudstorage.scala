package me.tracklist.cloudstorage

// Application config
import me.tracklist.ApplicationConfig

// Google credentials
import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential

// Http transport
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport

// Google Storage API
import com.google.api.services.storage.Storage
import com.google.api.services.storage.StorageScopes
import com.google.api.client.http.AbstractInputStreamContent
import com.google.api.client.http.FileContent
import com.google.api.client.http.ByteArrayContent

// Json stuff
import com.google.api.client.json.GenericJson
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.jackson2.JacksonFactory

// Java utils
import java.util.Collections
import java.io.File
import java.io.FileOutputStream

// Scala conversions
import scala.collection.JavaConverters._

object Cloudstorage {

  // Google credentials to access its services
  private val credential = new GoogleCredential.Builder()
    .setTransport(GoogleNetHttpTransport.newTrustedTransport())
    .setJsonFactory(JacksonFactory.getDefaultInstance())
    .setServiceAccountId(ApplicationConfig.GOOGLE_DEVELOPER_EMAIL)
    .setServiceAccountScopes(Collections.singleton(StorageScopes.DEVSTORAGE_READ_WRITE))
    .setServiceAccountPrivateKeyFromP12File(new File(ApplicationConfig.GOOGLE_DEVELOPER_KEY_PATH))
    .build()

  // Storage services
  private val storage = new Storage.Builder(
    GoogleNetHttpTransport.newTrustedTransport(),
    JacksonFactory.getDefaultInstance(),
    credential)
    .setHttpRequestInitializer(credential)
    .setApplicationName(ApplicationConfig.GOOGLE_PROJECT_NAME)
    .build()

  def listAndPrint = {
    val list = storage.objects.list(ApplicationConfig.GOOGLE_BUCKET_NAME).execute().getItems().asScala
    list.foreach(item => println(item.getName() + " (" + item.getSize() + " bytes)"))
  }

  def downloadObject(key : String, filename : String) = {
//    try {
      val fileOutputStream = new FileOutputStream(filename)
      // Cloudstorage request
      val objectRequest = storage.objects.get(ApplicationConfig.GOOGLE_BUCKET_NAME, key)
      // Execute request
      objectRequest.executeMediaAndDownloadTo(fileOutputStream)
//    } catch {
//      case e: java.io.IOException => println("Whatever IOExc")
//      case e: Exception => println("Whaterver Exc")
//    }
  }

  def uploadObject(key : String, filename : String, contentType : String) = {
//    try {
      // We fetch the contents from a file
      val objectContent = new FileContent(contentType, new File(filename))
      // Metadata, set to null
      val storageObject = null
      // Cloudstorage request
      val objectRequest = storage.objects.
        insert(ApplicationConfig.GOOGLE_BUCKET_NAME, storageObject, objectContent)
      // If we do not provide metadata we have to set name property
      // TODO provide metadata
      objectRequest.setName(key);
      // Execute request
      objectRequest.execute()
//    } catch {
//      case e: java.io.IOException => println(e.toString())
//      case e: Exception => println("Whaterver Exc")
//    }
  }

  def uploadObjectAsByteArray(key : String, content : String, contentType : String) = {
//    try {
      // We fetch the contents from a file
      val objectContent = ByteArrayContent.fromString(contentType, content)
      // Metadata, set to null
      val storageObject = null
      // Cloudstorage request
      val objectRequest = storage.objects.
        insert(ApplicationConfig.GOOGLE_BUCKET_NAME, storageObject, objectContent)
      // If we do not provide metadata we have to set name property
      // TODO provide metadata
      objectRequest.setName(key);
      // Execute request
      objectRequest.execute()
//    } catch {
//      case e: java.io.IOException => println(e.toString())
//      case e: Exception => println("Whaterver Exc")
//    }
  }

  def deleteObject(key: String) = {
//    try {
        val objectRequest = storage.objects.delete(ApplicationConfig.GOOGLE_BUCKET_NAME, key)
        objectRequest.execute()
//      } catch {
//        case e: java.io.IOException => println("IOException")
//        case e: Exception => println("Exception")
//      }
  }

}