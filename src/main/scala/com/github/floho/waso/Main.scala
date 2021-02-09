package com.github.floho.waso

import akka.actor.ActorSystem
import com.github.floho.waso.configuration.BotConfiguration
import com.github.floho.waso.controller.MainController
import com.github.floho.waso.repository.LogRepository
import com.github.floho.waso.service.{DiscordService, MongoService}
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}
import scala.util.control.NonFatal

object Main {

  private val logger: Logger = LoggerFactory.getLogger(getClass)
  private val config: BotConfiguration = new BotConfiguration

  def main(args: Array[String]): Unit = {

    val system = ActorSystem(config.name)
    implicit val dispatcher: ExecutionContext = system.dispatcher

    val mongoService: MongoService = new MongoService(config.mongo.connectionString, config.mongo.database)
    val discordService: DiscordService = new DiscordService(config.discord.token)

    system.registerOnTermination {
      mongoService.stop()
      discordService.stop()
    }

    mongoService.getCollection(config.mongo.collection).map { collection =>
      val logRepository = new LogRepository(collection)
      val mainController = new MainController(logRepository, discordService.gateway)
      mainController.start()
      logger.info("waso is here")
    }.recover {
      case NonFatal(ex) =>
        logger.error("Can't start controller", ex)
        system.terminate()
    }

    Await.result(system.whenTerminated, Duration.Inf)
  }

}
