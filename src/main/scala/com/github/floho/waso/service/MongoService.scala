package com.github.floho.waso.service

import reactivemongo.api.bson.collection.BSONCollection
import reactivemongo.api.{AsyncDriver, DB, MongoConnection}

import scala.concurrent.{ExecutionContext, Future}

class MongoService(connectionString: String, databaseName: String)(implicit ec: ExecutionContext) {
  private val driver = new AsyncDriver
  private val database: Future[DB] = for {
    uri <- MongoConnection.fromString(connectionString)
    conn <- driver.connect(uri)
    db <- conn.database(databaseName)
  } yield db

  def getCollection(collection: String): Future[BSONCollection] =
    database.map(_.collection(collection))

  def stop(): Future[Unit] = driver.close()
}
