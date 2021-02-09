package com.github.floho.waso.entity

import reactivemongo.api.bson.BSONDocument

object Filter {
  def unit: Filter = Filter(None, None, None, None, None)
}

final case class Filter(boss: Option[String],
                        player: Option[String],
                        account: Option[String],
                        profession: Option[String],
                        content: Option[String]) {
  override def toString: String = {
    val elems = Seq(
      boss.map(value => s"Boss: $value"),
      player.map(value => s"Player: $value"),
      account.map(value => s"Account: $value"),
      profession.map(value => s"Profession: $value"),
      content.map(value => s"Content: $value"),
    ).flatten
    if (elems.isEmpty) "No filters"
    else elems.mkString("\n")
  }

  def toBSONDocument: Option[BSONDocument] =
    Seq(
      content.map(value => BSONDocument("type" -> value)),
      boss.map(value => BSONDocument("boss" -> value)),
      account.map(value => BSONDocument("account" -> value)),
      player.map(value => BSONDocument("name" -> value)),
      profession.map(value => BSONDocument("profession" -> value)),
    ).flatten.reduceOption(_ ++ _)
}
