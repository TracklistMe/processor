package me.tracklist.entities

import spray.json._
import TracklistJsonProtocol._

case class Release (
  var id : Int,
  var title : Option[String],
  var releaseDate : Option[String],
  var isActive : Option[Boolean],
  var cover: Option[String],
  var catalogNumber: Option[String],
  var UPC: Option[String],
  var Grid: Option[String],
  var description: Option[String],
  var status: Option[String],
  // is named type in json
  //var releaseType: String = null
  var createdAt: Option[String],
  var updatedAt: Option[String],
  var processedAt: Option[String],
  var processingTime: Option[Long],

  val Tracks : Array[Track]) {}
 