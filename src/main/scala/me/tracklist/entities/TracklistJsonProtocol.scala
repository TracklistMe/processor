package me.tracklist.entities

import spray.json._

object TracklistJsonProtocol extends DefaultJsonProtocol {
  implicit val trackFormat = jsonFormat16(Track)
  implicit val releaseFormat = jsonFormat15(Release)
}
