package me.tracklist.entities

import spray.json._

object TracklistJsonProtocol extends DefaultJsonProtocol {
  implicit val trackFormat = jsonFormat8(Track)
  implicit val releaseFormat = jsonFormat13(Release)
}
