package com.github.floho.waso.service

import discord4j.core.{DiscordClient, GatewayDiscordClient}

class DiscordService(token: String) {
  private val client: DiscordClient = DiscordClient.create(token)
  val gateway: GatewayDiscordClient = client.login().block()

  def stop(): Unit = gateway.logout()
}
