package com.github.floho.waso.repository

import com.github.floho.waso.entity.{Filter, Log, LogRecord}
import reactivemongo.api.bson.collection.BSONCollection
import reactivemongo.api.bson.{BSONArray, BSONDocument, BSONDocumentReader, BSONInteger, BSONNull, BSONString, Macros}

import scala.concurrent.{ExecutionContext, Future}

class LogRepository(collection: BSONCollection)(implicit ec: ExecutionContext) {
  implicit val recordReader: BSONDocumentReader[LogRecord] = Macros.reader[LogRecord]
  implicit val logReader: BSONDocumentReader[Log] = Macros.reader[Log]

  import collection.AggregationFramework.{Avg, Group, Limit, Max, Min, Sum, Push, Sort, Descending, AddFields, Slice, AddToSet, Match}

  def getSet(field: String): Future[Set[String]] = {
    collection.distinct[String, Set](field)
  }

  def getLogsGroupedByBoss(filter: Filter): Future[List[Log]] = {
    collection.aggregatorContext[Log](
      pipeline = (filter.toBSONDocument.map(document => Match(document)) ++ Seq(
        Group(BSONDocument(
          "boss" -> "$boss",
          "profession" -> "$profession"
        ))(
          "minDps" -> Min(BSONDocument("$cond" -> BSONDocument(
            "if" -> "$success",
            "then" -> "$dpsTarget",
            "else" -> BSONNull
          ))),
          "avgDps" -> Avg(BSONDocument("$cond" -> BSONDocument(
            "if" -> "$success",
            "then" -> "$dpsTarget",
            "else" -> BSONNull
          ))),
          "maxDps" -> Max(BSONDocument("$cond" -> BSONDocument(
            "if" -> "$success",
            "then" -> "$dpsTarget",
            "else" -> BSONNull
          ))),
          "success" -> AddToSet(BSONDocument("$cond" -> BSONDocument(
            "if" -> "$success",
            "then" -> "$start",
            "else" -> "$$REMOVE"
          ))),
          "total" -> AddToSet(BSONString("$start")),
        ),
        AddFields(BSONDocument(
          "ord" -> BSONDocument("$size" -> "$total")
        )),
        Sort(Descending("ord")),
        Group(BSONString("$_id.boss"))(
          "totalSuccess" -> AddToSet(BSONString("$success")),
          "total" -> AddToSet(BSONString("$total")),
          "records" -> Push(BSONDocument(
            "profession" -> "$_id.profession",
            "minDps" -> "$minDps",
            "avgDps" -> "$avgDps",
            "maxDps" -> "$maxDps",
            "success" -> BSONDocument("$size" -> "$success"),
            "total" -> BSONDocument("$size" -> "$total"),
          ))
        ),
        AddFields(BSONDocument(
          "boss" -> "$_id",
          "totalSuccess" -> BSONDocument(
            "$size" -> BSONDocument(
              "$reduce" -> BSONDocument(
                "input" -> "$totalSuccess",
                "initialValue" -> BSONArray(),
                "in" -> BSONDocument("$setUnion" -> BSONArray("$$value", "$$this"))
              )
            )
          ),
          "total" -> BSONDocument(
            "$size" -> BSONDocument(
              "$reduce" -> BSONDocument(
                "input" -> "$total",
                "initialValue" -> BSONArray(),
                "in" -> BSONDocument("$setUnion" -> BSONArray("$$value", "$$this"))
              )
            )
          ),
          "records" -> Slice(BSONString("$records"), BSONInteger(5))
        )),
        Sort(Descending("total")),
        Limit(3)
      )).toList
    ).prepared.cursor.collect[List]()
  }
}
