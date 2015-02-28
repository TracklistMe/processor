package me.tracklist.utils

import scala.sys.process.ProcessLogger

class NullProcessLogger extends ProcessLogger {
  
  override def buffer[T](f: => T): T = f

  override def err(s: => String): Unit = {}

  override def out(s: ⇒ String): Unit = {}

}

object NullProcessLogger {
  def apply() = new NullProcessLogger()
}