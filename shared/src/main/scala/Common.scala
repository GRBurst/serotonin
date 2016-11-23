package serotonin
import concurrent.duration._

object Common {
  def localNow = System.nanoTime nanoseconds
  def globalNow = System.currentTimeMillis milliseconds
}
