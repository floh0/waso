package com.github.floho.waso.visualization

object ProgressBar {
  val empty: LazyList[String] = LazyList.continually("▱")
  val full: LazyList[String] = LazyList.continually("▰")

  def draw(min: Double, max: Double, width: Int): String = {
    val minWidth = ((min/max)*width).toInt
    full.take(minWidth).mkString("") + empty.take(width-minWidth).mkString("")
  }
}
