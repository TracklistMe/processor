package me.tracklist.entities

case class Track(
  var id: Int,
  var title: String,
  var path: String,
  var mp3Path: Option[String],
  var snippetPath: Option[String], 
  var version: Option[String],
  var cover: Option[String],
  var waveform: Option[String]) {

}

sealed trait TrackStatus
case object Fail extends TrackStatus
case object Success extends TrackStatus
case object Processing extends TrackStatus