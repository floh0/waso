package com.github.floho.waso.configuration

import com.typesafe.config.{Config, ConfigFactory}

object BotConfiguration {
  val path = "com.github.floho.waso"
  val configFile = "application.conf"
}

class BotConfiguration {
  private val config: Config = ConfigFactory
    .parseResources(BotConfiguration.configFile)
    .getConfig(BotConfiguration.path)
    .resolve()

  class Mongo {
    val connectionString: String = config.getString("mongo.connectionString")
    val database: String = config.getString("mongo.database")
    val collection: String = config.getString("mongo.collection")
  }

  class Discord {
    val token: String = config.getString("discord.token")
  }

  lazy val name: String = config.getString("name")
  lazy val mongo: Mongo = new Mongo
  lazy val discord: Discord = new Discord
}
