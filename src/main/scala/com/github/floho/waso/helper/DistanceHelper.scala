package com.github.floho.waso.helper

import scala.math.min

// Taken from https://gist.github.com/tixxit/1246894
object DistanceHelper {
  def getDistance(a: String, b: String): Int = {
    (a foldLeft (0 to b.length).toList)((prev, x) =>
      (prev zip prev.tail zip b).scanLeft(prev.head + 1) {
        case (h, ((d, v), y)) => min(min(h + 1, v + 1), d + (if (x.toLower == y.toLower) 0 else 2))
      }).last
  }
}
