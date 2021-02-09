package com.github.floho.waso.controller

import com.github.floho.waso.entity.{Filter, Log}
import com.github.floho.waso.helper.DistanceHelper
import com.github.floho.waso.repository.LogRepository
import com.github.floho.waso.visualization.{BossInfo, ProgressBar}
import com.google.common.cache.{Cache, CacheBuilder, RemovalNotification}
import discord4j.core.GatewayDiscordClient
import discord4j.core.`object`.entity.{Message, User}
import discord4j.core.`object`.reaction.ReactionEmoji
import discord4j.core.event.domain.message.{MessageCreateEvent, ReactionAddEvent}
import discord4j.rest.util.Color

import java.time.Instant
import java.util.concurrent.{ConcurrentHashMap, TimeUnit}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.matching.Regex

class MainController(logRepository: LogRepository, gateway: GatewayDiscordClient)(implicit ec: ExecutionContext) {
  val map: ConcurrentHashMap[User, Filter] = new ConcurrentHashMap[User, Filter]()

  val cache: Cache[(User, Message), Map[String, (String, String)]] = CacheBuilder.newBuilder()
    .expireAfterWrite(1, TimeUnit.MINUTES)
    .removalListener((notification: RemovalNotification[(User, Message), Map[String, (String, String)]]) => {
      notification.getKey match {
        case (_, message) => message.removeAllReactions().block()
      }
    })
    .build[(User, Message), Map[String, (String, String)]]()

  val transformations: Map[String, (Option[String], Option[Filter]) => Filter] = Map(
    "boss" -> { (value: Option[String], filter: Option[Filter]) =>
      filter.map(_.copy(boss = value)).getOrElse(Filter(value, None, None, None, None))
    },
    "player" -> { (value: Option[String], filter: Option[Filter]) =>
      filter.map(_.copy(player = value)).getOrElse(Filter(None, value, None, None, None))
    },
    "account" -> { (value: Option[String], filter: Option[Filter]) =>
      filter.map(_.copy(account = value)).getOrElse(Filter(None, None, value, None, None))
    },
    "profession" -> { (value: Option[String], filter: Option[Filter]) =>
      filter.map(_.copy(profession = value)).getOrElse(Filter(None, None, None, value, None))
    },
    "content" -> { (value: Option[String], filter: Option[Filter]) =>
      filter.map(_.copy(content = value)).getOrElse(Filter(None, None, None, None, value))
    }
  )

  val sets: Map[String, Future[Set[String]]] = Map(
    "boss" -> logRepository.getSet("boss"),
    "player" -> logRepository.getSet("name"),
    "account" -> logRepository.getSet("account"),
    "profession" -> logRepository.getSet("profession"),
    "content" -> logRepository.getSet("type"),
  )

  val numbers = Seq("1️⃣", "2️⃣", "3️⃣", "4️⃣", "5️⃣")

  val progressBarWidth: Int = 14

  val commandParser: Regex = "^([^\\s]+)+ (.+)$".r

  val commands: Map[String => Boolean, (String, Message) => Unit] = Map(
    {
      (_: String) == "mu"
    } -> {
      (_: String, message: Message) => message.getChannel.flatMap(_.createMessage("mu")).block()
    }, {
      (_: String) == "help"
    } -> {
      (_: String, message: Message) => message.getChannel.flatMap(_.createMessage(
        """mi waso :duck:
          |```
          |waso mu: Check if I am here
          |waso help: Display this message
          |
          |waso <filter> <value>: Set filter to given value.
          | Possible filters are:
          | → boss: Boss name
          | → player: Character name
          | → account: Account name
          | → profession: Profession, elite specializations count as independent professions
          | → content: Either 'raid', 'fractal' or 'strike'
          | If the value is not in database, some proposals will be given
          |
          |waso clear <filter / all>: Remove the current value of the filter
          | → filter: accepted filters are 'boss', 'player', 'account', 'profession' and 'content'
          | → if 'all' is given, all the filters are removed
          |
          |waso stats: Give the stats according to the filters
          | → a maximum of 3 bosses and 5 professions per boss are returned
          |```""".stripMargin)).block()
    }, {
      (command: String) => commandParser.findPrefixMatchOf(command).isDefined
    } -> {
      (command: String, message: Message) =>
        for {
          regexMatch <- commandParser.findPrefixMatchOf(command)
          (field1, field2) = (regexMatch.group(1), regexMatch.group(2))
          (returnedTransformation, possibleKey, value) = {
            if (field1 == "clear") {
              if (field2 == "all") (None, None, None)
              else (transformations.get(field2), Some(field2), None)
            }
            else (transformations.get(field1), Some(field1), Some(field2))
          }
        } yield {
          val user = message.getAuthor.get()
          val response = {
            possibleKey match {
              case None =>
                map.remove(user)
                Future.successful((s"Filters cleared for user ${user.getUsername}.", Seq.empty))
              case Some(key) =>
                returnedTransformation.map { transformation =>
                  map.compute(user, (_, currentFilter) => transformation(value, Option(currentFilter)))
                  val advice = for {
                    realValue <- value
                    set <- sets.get(key)
                  } yield for {
                    realSet <- set
                  } yield {
                    if (!realSet.contains(realValue)) {
                      val m = realSet.map(x => DistanceHelper.getDistance(x, realValue)).min
                      val filtered = realSet.filter(x => DistanceHelper.getDistance(x, realValue) == m)
                      val zippedNumbers = numbers.zip(filtered)
                      val advice = zippedNumbers.map { case (emoji, maybe) => s"$emoji $maybe" }
                      Some((
                        s"""I don't know `$realValue`. Did you mean one of these?
                           |${advice.mkString("\n")}""".stripMargin,
                        zippedNumbers.map { case (emoji, value) => (emoji, (key, value)) }
                      ))
                    } else None
                  }

                  val update = value
                    .map(x => s"`$key` set to `$x` for user ${user.getUsername}.")
                    .getOrElse(s"`$key` cleared for user ${user.getUsername}.")

                  advice
                    .map(future => future.map(elem =>
                      elem
                        .map { case (value, reactions) => (s"$update\n$value", reactions) }
                        .getOrElse((update, Seq.empty))
                    ))
                    .getOrElse(Future.successful((update, Seq.empty)))
                }.getOrElse(Future.successful((s"I don't know `$key`.", Seq.empty)))
            }
          }
          response.foreach { case (body, reactions) =>
            val newMessage: Message = message.getChannel
              .flatMap(_.createMessage(body))
              .block()
            reactions.foreach { case (emoji, _) =>
              newMessage.addReaction(ReactionEmoji.unicode(emoji)).block()
            }
            cache.put((user, newMessage), reactions.toMap)
          }
        }
    }, {
      (_: String) == "stats"
    } -> {
      (_: String, message: Message) =>
        val (filter, result) = countCurrent(message.getAuthor.get())
        result.foreach { documents =>
          val author = message.getAuthor.get()
          if (documents.isEmpty) message.getChannel.flatMap(_.createMessage(
            s"""No record found with filters:
               |$filter""".stripMargin
          )).block()
          documents.foreach { document =>
            val bossIcon = BossInfo.icons.get(document.boss)
            val maxDps = document.records.maxBy(_.maxDps).maxDps.getOrElse(0.toDouble)
            message.getChannel.flatMap(_.createEmbed { spec =>
              val stats = document.records.map { record =>
                s"""***${record.profession}***
                   |**${record.success}** kills / **${record.total}** tries
                   |DPS from **${record.minDps.map(_.toInt).getOrElse("null")}** to **${record.maxDps.map(_.toInt).getOrElse("null")}** (avg **${record.avgDps.map(_.toInt).getOrElse("null")}**)
                   |${ProgressBar.draw(record.avgDps.getOrElse(0.toDouble), record.maxDps.getOrElse(0.toDouble), ((record.maxDps.getOrElse(0.toDouble) / maxDps) * progressBarWidth).toInt)}""".stripMargin
              }
              val embed =
                spec.setColor(Color.GREEN)
                  .setAuthor(filter.toString, null, author.getAvatarUrl)
                  .setDescription(
                    s"""**${document.boss.toUpperCase}**
                       |**${document.totalSuccess}** kills / **${document.total}** tries
                       |
                       |${stats.mkString("\n\n")}""".stripMargin)
                  .setFooter("waso li lon", null)
                  .setTimestamp(Instant.now())
              bossIcon.map(icon => embed.setThumbnail(icon)).getOrElse(embed)
            }).block()
          }
        }
    }
  )

  def start(): Unit = {
    gateway.on(classOf[MessageCreateEvent])
      .map(event => event.getMessage)
      .filter(message => message.getAuthor.map[Boolean](user => !user.isBot).orElse(false))
      .map { message =>
        val content = message.getContent
        if (content.startsWith("waso ")) {
          val command = content.drop(5)
          commands.foreach {
            case (check, action) => if (check(command)) action(command, message)
          }
        }
      }
      .subscribe()


    gateway.on(classOf[ReactionAddEvent])
      .flatMap(event =>
        for {
          user <- event.getUser
          message <- event.getMessage
        } yield {
          if (!user.isBot) {
            for {
              reactionMap <- Option(cache.getIfPresent((user, message)))
              emoji <- event.getEmoji.asUnicodeEmoji().map[Option[ReactionEmoji.Unicode]](x => Some(x)).orElse(None)
              unicode = emoji.getRaw
              (key, associatedValue) <- reactionMap.get(unicode)
              transformation <- transformations.get(key)
            } yield {
              map.compute(user, (_, currentFilter) => transformation(Some(associatedValue), Option(currentFilter)))
              cache.invalidate((user, message))
              message.edit(editSpec => editSpec.setContent(s"`$key` set to `$associatedValue` for user ${user.getUsername}.")).block()
              message.removeAllReactions().block()
            }
          }
        }
      )
      .subscribe()

  }

  private def countCurrent(user: User): (Filter, Future[List[Log]]) = {
    val filter = Option(map.get(user)).getOrElse(Filter.unit)
    (filter, logRepository.getLogsGroupedByBoss(filter))
  }
}
